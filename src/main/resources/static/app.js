(() => {
    "use strict";

    const state = {
        token: sessionStorage.getItem("finledger_token"),
        user: null,
        accounts: [],
        recentTransactions: [],
        transactionPage: 1,
        transactionTotalPages: 1,
        idempotencyKey: createIdempotencyKey()
    };

    const elements = {
        authView: document.querySelector("#auth-view"),
        appView: document.querySelector("#app-view"),
        loginTab: document.querySelector("#login-tab"),
        registerTab: document.querySelector("#register-tab"),
        loginForm: document.querySelector("#login-form"),
        registerForm: document.querySelector("#register-form"),
        authTitle: document.querySelector("#auth-title"),
        authSubtitle: document.querySelector("#auth-subtitle"),
        rechargeModal: document.querySelector("#recharge-modal")
    };

    class ApiRequestError extends Error {
        constructor(message, status, code) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    async function request(path, options = {}) {
        const headers = new Headers(options.headers || {});
        if (options.body && !headers.has("Content-Type")) {
            headers.set("Content-Type", "application/json");
        }
        if (state.token) {
            headers.set("Authorization", `Bearer ${state.token}`);
        }

        let response;
        try {
            response = await fetch(path, {...options, headers});
        } catch (error) {
            throw new ApiRequestError("无法连接服务，请确认 FinLedger 正在运行", 0, "NETWORK_ERROR");
        }

        const contentType = response.headers.get("content-type") || "";
        const body = contentType.includes("application/json") ? await response.json() : null;

        if (!response.ok) {
            if (response.status === 401 && !path.includes("/api/auth/login")) {
                clearSession();
                showAuth();
            }
            const fieldMessage = body?.fieldErrors?.map(item => `${item.field}: ${item.message}`).join("；");
            throw new ApiRequestError(
                fieldMessage || translateError(body?.code, body?.message) || `请求失败（${response.status}）`,
                response.status,
                body?.code
            );
        }
        return body;
    }

    function translateError(code, fallback) {
        const messages = {
            INVALID_CREDENTIALS: "用户名或密码错误",
            USERNAME_ALREADY_EXISTS: "该用户名已经存在",
            ACCOUNT_NOT_FOUND: "账户不存在",
            ACCOUNT_ACCESS_DENIED: "你没有权限操作该账户",
            ACCOUNT_NOT_ACTIVE: "账户当前不可用",
            INSUFFICIENT_BALANCE: "付款账户余额不足",
            SAME_ACCOUNT_TRANSFER: "付款账户与收款账户不能相同",
            INVALID_AMOUNT: "请输入合法的两位小数金额",
            IDEMPOTENCY_CONFLICT: "这个幂等键已用于另一笔转账",
            RATE_LIMIT_EXCEEDED: "操作过于频繁，请稍后再试",
            UNSUPPORTED_AI_QUERY: "助手仅支持只读交易分析问题",
            AI_PROVIDER_UNAVAILABLE: "AI 服务暂时不可用"
        };
        return messages[code] || fallback;
    }

    function createIdempotencyKey() {
        const value = typeof crypto.randomUUID === "function"
            ? crypto.randomUUID()
            : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
        return `web-transfer-${value}`;
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function formatMoney(value) {
        const numeric = Number(value || 0);
        return new Intl.NumberFormat("zh-CN", {
            style: "currency",
            currency: "CNY",
            minimumFractionDigits: 2
        }).format(Number.isFinite(numeric) ? numeric : 0);
    }

    function parseUtcDate(value) {
        if (!value) return null;
        const normalized = /(?:Z|[+-]\d\d:\d\d)$/.test(value) ? value : `${value}Z`;
        const date = new Date(normalized);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    function formatDate(value, includeYear = true) {
        const date = parseUtcDate(value);
        if (!date) return "—";
        return new Intl.DateTimeFormat("zh-CN", {
            ...(includeYear ? {year: "numeric"} : {}),
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
            hour12: false
        }).format(date);
    }

    function formatAccountNumber(accountNo) {
        const raw = String(accountNo || "");
        if (raw.length <= 8) return raw;
        return `${raw.slice(0, 4)} ···· ${raw.slice(-4)}`;
    }

    function showToast(message, type = "success") {
        const region = document.querySelector("#toast-region");
        const toast = document.createElement("div");
        toast.className = `toast ${type === "error" ? "error" : ""}`;
        toast.innerHTML = `<span class="toast-mark">${type === "error" ? "!" : "✓"}</span><span>${escapeHtml(message)}</span>`;
        region.append(toast);
        window.setTimeout(() => toast.remove(), 4200);
    }

    function setSubmitting(form, submitting, label) {
        const button = form.querySelector('button[type="submit"]');
        if (!button) return;
        if (submitting) {
            button.dataset.originalLabel = button.innerHTML;
            button.textContent = label || "处理中…";
            button.disabled = true;
        } else {
            button.innerHTML = button.dataset.originalLabel || button.innerHTML;
            button.disabled = false;
        }
    }

    async function checkHealth() {
        const dot = document.querySelector("#auth-health-dot");
        const text = document.querySelector("#auth-health-text");
        try {
            const health = await request("/api/health");
            dot.classList.remove("offline");
            text.textContent = health.status === "UP" ? "FinLedger 服务运行正常" : "服务状态未知";
        } catch (error) {
            dot.classList.add("offline");
            text.textContent = "暂时无法连接后端服务";
        }
    }

    function selectAuthMode(mode) {
        const login = mode === "login";
        elements.loginTab.classList.toggle("active", login);
        elements.registerTab.classList.toggle("active", !login);
        elements.loginTab.setAttribute("aria-selected", String(login));
        elements.registerTab.setAttribute("aria-selected", String(!login));
        elements.loginForm.classList.toggle("hidden", !login);
        elements.registerForm.classList.toggle("hidden", login);
        elements.authTitle.textContent = login ? "登录资金工作台" : "创建模拟用户";
        elements.authSubtitle.textContent = login
            ? "使用你的账户继续访问安全工作区。"
            : "注册后即可创建账户并体验完整资金流程。";
    }

    function showAuth() {
        elements.authView.classList.remove("hidden");
        elements.appView.classList.add("hidden");
    }

    function showApp() {
        elements.authView.classList.add("hidden");
        elements.appView.classList.remove("hidden");
    }

    function clearSession() {
        state.token = null;
        state.user = null;
        state.accounts = [];
        sessionStorage.removeItem("finledger_token");
    }

    async function refreshWorkspace() {
        const [user, accounts, transactions] = await Promise.all([
            request("/api/users/me"),
            request("/api/accounts"),
            request("/api/transactions?page=1&size=8")
        ]);
        state.user = user;
        state.accounts = accounts;
        state.recentTransactions = transactions.items || [];
        renderWorkspace();
    }

    function renderWorkspace() {
        document.querySelector("#current-username").textContent = state.user?.username || "用户";
        document.querySelector("#account-count").textContent = String(state.accounts.length);
        const total = state.accounts.reduce((sum, account) => sum + Number(account.balance || 0), 0);
        document.querySelector("#total-balance").textContent = formatMoney(total);

        renderAccountGrid(document.querySelector("#overview-account-grid"), state.accounts.slice(0, 3), true);
        renderAccountGrid(document.querySelector("#account-page-grid"), state.accounts, false);
        renderOverviewTransactions();
        renderAccountOptions();
    }

    function renderAccountGrid(container, accounts, compact) {
        if (!accounts.length) {
            container.innerHTML = `
                <div class="empty-account-card">
                    <div><strong>还没有资金账户</strong><p>创建第一个模拟账户开始体验。</p>
                    <button class="button button-outline button-small" data-create-account type="button">创建账户</button></div>
                </div>`;
            return;
        }
        container.innerHTML = accounts.map(account => `
            <article class="account-card">
                <div class="account-card-header">
                    <span class="account-status">${escapeHtml(account.status)}</span>
                    <span class="account-id">ID ${escapeHtml(account.id)}</span>
                </div>
                <div class="account-number">${escapeHtml(formatAccountNumber(account.accountNo))}</div>
                <div class="account-balance">${escapeHtml(formatMoney(account.balance))}</div>
                <div class="account-card-footer">
                    <span>${escapeHtml(account.currency)} · VERSION ${escapeHtml(account.version)}</span>
                    <button data-recharge-account="${escapeHtml(account.id)}" type="button">模拟充值 ＋</button>
                </div>
            </article>`).join("");

        if (compact && state.accounts.length > accounts.length) {
            container.insertAdjacentHTML("beforeend", "");
        }
    }

    function renderAccountOptions() {
        const optionHtml = state.accounts.map(account =>
            `<option value="${escapeHtml(account.id)}">账户 ${escapeHtml(account.id)} · ${escapeHtml(formatAccountNumber(account.accountNo))} · ${escapeHtml(formatMoney(account.balance))}</option>`
        ).join("");
        document.querySelector("#transfer-from").innerHTML = optionHtml || '<option value="">请先创建账户</option>';
        document.querySelector("#recharge-account").innerHTML = optionHtml || '<option value="">请先创建账户</option>';
        document.querySelector("#filter-account").innerHTML = `<option value="">全部账户</option>${optionHtml}`;
        document.querySelector("#idempotency-key").textContent = state.idempotencyKey;
    }

    function transactionMeta(item) {
        const credit = item.direction === "CREDIT";
        const recharge = item.businessType === "RECHARGE";
        return {
            credit,
            title: recharge ? "模拟充值" : credit ? "转账收入" : "转账支出",
            subtitle: recharge ? `充值业务 #${item.businessId}` : `对方账户 #${item.counterpartyAccountId ?? "—"}`,
            sign: credit ? "+" : "−"
        };
    }

    function renderOverviewTransactions() {
        const body = document.querySelector("#overview-transaction-body");
        const empty = document.querySelector("#overview-empty-transactions");
        const items = state.recentTransactions;
        body.innerHTML = items.map(item => {
            const meta = transactionMeta(item);
            return `<tr>
                <td><div class="transaction-title"><span class="transaction-symbol ${meta.credit ? "" : "debit"}">${meta.credit ? "↓" : "↑"}</span><span><strong>${meta.title}</strong><small>${escapeHtml(meta.subtitle)}</small></span></div></td>
                <td>#${escapeHtml(item.accountId)}</td>
                <td>${escapeHtml(formatDate(item.createdAt, false))}</td>
                <td class="align-right ${meta.credit ? "amount-credit" : "amount-debit"}">${meta.sign}${escapeHtml(formatMoney(item.amount))}</td>
                <td class="align-right">${escapeHtml(formatMoney(item.balanceAfter))}</td>
            </tr>`;
        }).join("");
        empty.classList.toggle("hidden", items.length > 0);
        body.closest(".table-scroll").classList.toggle("hidden", items.length === 0);
    }

    async function loadTransactions(page = 1) {
        const params = new URLSearchParams({page: String(page), size: "20"});
        const accountId = document.querySelector("#filter-account").value;
        const businessType = document.querySelector("#filter-business").value;
        const direction = document.querySelector("#filter-direction").value;
        if (accountId) params.set("accountId", accountId);
        if (businessType) params.set("businessType", businessType);
        if (direction) params.set("direction", direction);

        const result = await request(`/api/transactions?${params}`);
        state.transactionPage = result.page || 1;
        state.transactionTotalPages = Math.max(result.totalPages || 0, 1);
        renderTransactionPage(result);
    }

    function renderTransactionPage(result) {
        const items = result.items || [];
        const body = document.querySelector("#transaction-body");
        const empty = document.querySelector("#transaction-empty");
        body.innerHTML = items.map(item => {
            const meta = transactionMeta(item);
            return `<tr>
                <td><div class="transaction-title"><span class="transaction-symbol ${meta.credit ? "" : "debit"}">${meta.credit ? "↓" : "↑"}</span><span><strong>${escapeHtml(item.recordNo)}</strong><small>${escapeHtml(meta.subtitle)}</small></span></div></td>
                <td>${escapeHtml(meta.title)}</td>
                <td>#${escapeHtml(item.accountId)}</td>
                <td>${escapeHtml(formatDate(item.createdAt))}</td>
                <td>${escapeHtml(formatMoney(item.balanceBefore))} → ${escapeHtml(formatMoney(item.balanceAfter))}</td>
                <td class="align-right ${meta.credit ? "amount-credit" : "amount-debit"}">${meta.sign}${escapeHtml(formatMoney(item.amount))}</td>
            </tr>`;
        }).join("");
        body.closest(".table-scroll").classList.toggle("hidden", items.length === 0);
        empty.classList.toggle("hidden", items.length > 0);
        document.querySelector("#transaction-total").textContent = `共 ${result.total || 0} 条记录`;
        document.querySelector("#page-indicator").textContent = `${state.transactionPage} / ${state.transactionTotalPages}`;
        document.querySelector("#page-prev").disabled = state.transactionPage <= 1;
        document.querySelector("#page-next").disabled = state.transactionPage >= state.transactionTotalPages;
    }

    async function createAccount(button) {
        button.disabled = true;
        try {
            const account = await request("/api/accounts", {method: "POST"});
            showToast(`账户 #${account.id} 创建成功`);
            await refreshWorkspace();
        } catch (error) {
            showToast(error.message, "error");
        } finally {
            button.disabled = false;
        }
    }

    function openRechargeModal(accountId) {
        if (!state.accounts.length) {
            showToast("请先创建一个资金账户", "error");
            return;
        }
        if (accountId) document.querySelector("#recharge-account").value = String(accountId);
        elements.rechargeModal.classList.remove("hidden");
        window.setTimeout(() => document.querySelector("#recharge-amount").focus(), 50);
    }

    function closeRechargeModal() {
        elements.rechargeModal.classList.add("hidden");
    }

    async function handleRecharge(event) {
        event.preventDefault();
        const form = event.currentTarget;
        const accountId = document.querySelector("#recharge-account").value;
        const amount = document.querySelector("#recharge-amount").value;
        setSubmitting(form, true, "充值处理中…");
        try {
            const result = await request(`/api/accounts/${accountId}/recharges`, {
                method: "POST",
                body: JSON.stringify({amount: Number(amount)})
            });
            showToast(`充值成功，当前余额 ${formatMoney(result.balance)}`);
            form.reset();
            closeRechargeModal();
            await refreshWorkspace();
        } catch (error) {
            showToast(error.message, "error");
        } finally {
            setSubmitting(form, false);
        }
    }

    async function handleTransfer(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!state.accounts.length) {
            showToast("请先创建付款账户", "error");
            return;
        }
        const payload = {
            fromAccountId: Number(document.querySelector("#transfer-from").value),
            toAccountId: Number(document.querySelector("#transfer-to").value),
            amount: Number(document.querySelector("#transfer-amount").value)
        };
        setSubmitting(form, true, "事务处理中…");
        try {
            const result = await request("/api/transfers", {
                method: "POST",
                headers: {"Idempotency-Key": state.idempotencyKey},
                body: JSON.stringify(payload)
            });
            showToast(`转账 ${formatMoney(result.amount)} 成功，订单 ${result.transferNo}`);
            state.idempotencyKey = createIdempotencyKey();
            form.reset();
            await refreshWorkspace();
            document.querySelector("#idempotency-key").textContent = state.idempotencyKey;
        } catch (error) {
            showToast(error.message, "error");
        } finally {
            setSubmitting(form, false);
        }
    }

    async function handleAiQuestion(question, form) {
        const responseBox = document.querySelector("#ai-response");
        responseBox.innerHTML = '<span class="assistant-avatar">FL</span><div><strong>正在分析交易…</strong><p>Java 服务正在完成权限校验和受控数据查询。</p></div>';
        setSubmitting(form, true, "分析中…");
        try {
            const result = await request("/api/ai/transactions/query", {
                method: "POST",
                body: JSON.stringify({question})
            });
            const rows = (result.transactions || []).map(item => `
                <div class="ai-result-item"><span>${escapeHtml(formatDate(item.createdAt))} · 账户 #${escapeHtml(item.accountId)}</span><strong>${escapeHtml(formatMoney(item.amount))}</strong></div>`).join("");
            responseBox.innerHTML = `
                <span class="assistant-avatar">FL</span>
                <div>
                    <strong>${escapeHtml(result.answer)}</strong>
                    <p>${escapeHtml(formatDate(result.periodStart))} 至 ${escapeHtml(formatDate(result.periodEnd))} · ${escapeHtml(result.intent)}</p>
                    <div class="ai-metrics">
                        <div class="ai-metric"><span>统计金额</span><strong>${escapeHtml(formatMoney(result.totalAmount))}</strong></div>
                        <div class="ai-metric"><span>交易笔数</span><strong>${escapeHtml(result.transactionCount)}</strong></div>
                    </div>
                    ${rows ? `<div class="ai-result-list">${rows}</div>` : ""}
                </div>`;
        } catch (error) {
            responseBox.innerHTML = `<span class="assistant-avatar">!</span><div><strong>暂时无法完成分析</strong><p>${escapeHtml(error.message)}</p></div>`;
        } finally {
            setSubmitting(form, false);
        }
    }

    async function handleLogin(event) {
        event.preventDefault();
        const form = event.currentTarget;
        setSubmitting(form, true, "正在验证…");
        try {
            const result = await request("/api/auth/login", {
                method: "POST",
                body: JSON.stringify({
                    username: document.querySelector("#login-username").value,
                    password: document.querySelector("#login-password").value
                })
            });
            state.token = result.accessToken;
            sessionStorage.setItem("finledger_token", state.token);
            await refreshWorkspace();
            showApp();
            navigateTo("overview");
            showToast("登录成功，欢迎回到 FinLedger");
            form.reset();
        } catch (error) {
            showToast(error.message, "error");
        } finally {
            setSubmitting(form, false);
        }
    }

    async function handleRegister(event) {
        event.preventDefault();
        const form = event.currentTarget;
        const username = document.querySelector("#register-username").value.trim();
        const password = document.querySelector("#register-password").value;
        setSubmitting(form, true, "正在创建…");
        try {
            await request("/api/users", {
                method: "POST",
                body: JSON.stringify({username, password})
            });
            document.querySelector("#login-username").value = username;
            selectAuthMode("login");
            document.querySelector("#login-password").focus();
            form.reset();
            showToast("用户创建成功，请登录");
        } catch (error) {
            showToast(error.message, "error");
        } finally {
            setSubmitting(form, false);
        }
    }

    function navigateTo(viewName) {
        document.querySelectorAll(".page-view").forEach(view =>
            view.classList.toggle("active", view.id === `view-${viewName}`));
        document.querySelectorAll(".nav-item").forEach(item =>
            item.classList.toggle("active", item.dataset.view === viewName));
        window.scrollTo({top: 0, behavior: "smooth"});

        if (viewName === "transactions") {
            loadTransactions(1).catch(error => showToast(error.message, "error"));
        }
    }

    function bindEvents() {
        elements.loginTab.addEventListener("click", () => selectAuthMode("login"));
        elements.registerTab.addEventListener("click", () => selectAuthMode("register"));
        elements.loginForm.addEventListener("submit", handleLogin);
        elements.registerForm.addEventListener("submit", handleRegister);
        document.querySelector("#logout-button").addEventListener("click", () => {
            clearSession();
            showAuth();
            selectAuthMode("login");
            showToast("已安全退出登录");
        });

        document.querySelectorAll(".nav-item").forEach(item =>
            item.addEventListener("click", () => navigateTo(item.dataset.view)));
        document.querySelectorAll("[data-go]").forEach(item =>
            item.addEventListener("click", () => navigateTo(item.dataset.go)));
        document.querySelectorAll(".quick-action").forEach(item => item.addEventListener("click", () => {
            if (item.dataset.action === "recharge") openRechargeModal();
            else navigateTo(item.dataset.action);
        }));

        document.querySelector("#top-create-account").addEventListener("click", event => createAccount(event.currentTarget));
        document.querySelector("#create-account-button").addEventListener("click", event => createAccount(event.currentTarget));
        document.addEventListener("click", event => {
            const createButton = event.target.closest("[data-create-account]");
            if (createButton) createAccount(createButton);
            const rechargeButton = event.target.closest("[data-recharge-account]");
            if (rechargeButton) openRechargeModal(rechargeButton.dataset.rechargeAccount);
            if (event.target.closest("[data-close-modal]")) closeRechargeModal();
        });
        document.addEventListener("keydown", event => {
            if (event.key === "Escape" && !elements.rechargeModal.classList.contains("hidden")) closeRechargeModal();
        });

        document.querySelector("#recharge-form").addEventListener("submit", handleRecharge);
        document.querySelector("#transfer-form").addEventListener("submit", handleTransfer);
        document.querySelector("#transaction-filter").addEventListener("submit", event => {
            event.preventDefault();
            loadTransactions(1).catch(error => showToast(error.message, "error"));
        });
        document.querySelector("#page-prev").addEventListener("click", () =>
            loadTransactions(state.transactionPage - 1).catch(error => showToast(error.message, "error")));
        document.querySelector("#page-next").addEventListener("click", () =>
            loadTransactions(state.transactionPage + 1).catch(error => showToast(error.message, "error")));

        document.querySelector("#ai-form").addEventListener("submit", event => {
            event.preventDefault();
            const question = document.querySelector("#ai-question").value.trim();
            if (question) handleAiQuestion(question, event.currentTarget);
        });
        document.querySelectorAll("[data-question]").forEach(button => button.addEventListener("click", () => {
            const input = document.querySelector("#ai-question");
            input.value = button.dataset.question;
            handleAiQuestion(button.dataset.question, document.querySelector("#ai-form"));
        }));
    }

    async function initialize() {
        const today = new Intl.DateTimeFormat("zh-CN", {
            year: "numeric", month: "long", day: "numeric", weekday: "long"
        }).format(new Date());
        document.querySelector("#today-label").textContent = today;
        bindEvents();
        checkHealth();

        if (!state.token) {
            showAuth();
            return;
        }
        try {
            await refreshWorkspace();
            showApp();
        } catch (error) {
            clearSession();
            showAuth();
            if (error.status !== 401) showToast(error.message, "error");
        }
    }

    initialize();
})();
