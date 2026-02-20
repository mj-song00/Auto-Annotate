let isUnauthorizedHandled = false;

function getAccessToken() {
    return localStorage.getItem("accessToken");
}

function handleUnauthorized() {
    if (isUnauthorizedHandled) return;
    isUnauthorizedHandled = true;

    alert("인증이 만료되었습니다.");
    localStorage.removeItem("accessToken");
    window.location.href = "/login";
}

async function checkAuth() {
    const token = getAccessToken();

    // accessToken이 없으면 refresh로 재발급 먼저 시도
    if (!token || token.trim() === "") {
        try {
            const refreshResponse = await fetch("/api/v1/auth/refresh-token", {
                method: "POST",
                credentials: "include"
            });

            if (!refreshResponse.ok) {
                alert("로그인이 필요합니다.");
                window.location.href = "/login";
                return false;
            }

            const refreshResult = await refreshResponse.json().catch(() => null);
            const newAccessToken = refreshResult?.data?.accessToken;

            if (!newAccessToken || String(newAccessToken).trim() === "") {
                alert("로그인이 필요합니다.");
                window.location.href = "/login";
                return false;
            }

            localStorage.setItem("accessToken", newAccessToken);
            return true;
        } catch (e) {
            alert("로그인이 필요합니다.");
            window.location.href = "/login";
            return false;
        }
    }
    return true;
}

async function logout(){
    const logoutBtn = document.getElementById("logoutBtn");
    if (!logoutBtn) return;

    logoutBtn.addEventListener("click", () => {
        localStorage.removeItem("accessToken");
        window.location.href = "/login";
    });
}

window.addEventListener("load", async () => {
    const ok = await checkAuth();
    if (!ok) return;

    logout();
});