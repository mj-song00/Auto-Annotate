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
    // accessToken이 있어도 만료(exp)면 refresh 시도
    let shouldRefresh = false;

    if (!token || token.trim() === "") {
        shouldRefresh = true;
    } else {
        try {
            const parts = token.split(".");
            if (parts.length !== 3) {
                shouldRefresh = true;
            } else {
                const payloadJson = atob(parts[1].replace(/-/g, "+").replace(/_/g, "/"));
                const payload = JSON.parse(payloadJson);
                const expSec = payload && payload.exp;

                if (!expSec || typeof expSec !== "number") {
                    shouldRefresh = true;
                } else {
                    const nowSec = Math.floor(Date.now() / 1000);
                    // exp가 현재보다 작거나 같으면 만료
                    if (expSec <= nowSec) {
                        shouldRefresh = true;
                    }
                }
            }
        } catch (e) {
            shouldRefresh = true;
        }
    }

    if (shouldRefresh) {
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

            // 백엔드가 ResponseEntity<String> 으로 accessToken 문자열을 내려줌
            const newAccessToken = await refreshResponse.text();

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

    logoutBtn.addEventListener("click", async () => {
        try {
            await fetch("/api/v1/users/auth/logout", {
                method: "POST",
                credentials: "include"
            });
            console.log(res.status);
        } catch (e) {
            console.log("logout fetch failed:", e);
        }

        localStorage.removeItem("accessToken");
        window.location.href = "/login";
    });
}

window.addEventListener("load", async () => {
    const ok = await checkAuth();
    if (!ok) return;

    logout();
});