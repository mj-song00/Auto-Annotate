let currentProfile = null;

function getEls() {
    return {
        overlay: document.getElementById("modalOverlay"),
        modalTitle: document.getElementById("modalTitle"),
        modalBody: document.getElementById("modalBody"),
        closeBtn: document.getElementById("modalCloseBtn"),
        cancelBtn: document.getElementById("modalCancelBtn"),
        confirmBtn: document.getElementById("modalConfirmBtn"),
        nicknameText: document.getElementById("nicknameText")
    };
}

function closeModal() {
    const { overlay } = getEls();
    if (!overlay) return;
    overlay.hidden = true;
    document.body.style.overflow = "";
}

function showModal() {
    const { overlay } = getEls();
    if (!overlay) return;
    overlay.hidden = false;
    document.body.style.overflow = "hidden";
}

function renderTemplate(key) {
    const { modalBody } = getEls();
    if (!modalBody) return;

    const tpl = document.getElementById(`tpl-${key}`);
    modalBody.innerHTML = "";
    if (tpl) modalBody.appendChild(tpl.content.cloneNode(true));
}

function setModalUiByKey(key) {
    const { modalTitle, modalBody, confirmBtn } = getEls();
    if (!modalTitle || !modalBody || !confirmBtn) return;

    if (key === "nickname") {
        modalTitle.textContent = "닉네임 변경";
        confirmBtn.textContent = "저장";
        confirmBtn.disabled = false;
        return;
    }

    if (key === "password") {
        modalTitle.textContent = "비밀번호 변경";
        confirmBtn.textContent = "변경";
        confirmBtn.disabled = false;
        return;
    }

    if (key === "withdraw") {
        modalTitle.textContent = "회원 탈퇴";
        confirmBtn.textContent = "탈퇴";
        confirmBtn.disabled = true;

        const chk = modalBody.querySelector("#withdrawAgreeChk");
        if (chk) {
            chk.addEventListener("change", () => {
                confirmBtn.disabled = !chk.checked;
            });
        }
    }
}

function extractErrorMessage(result, fallback) {
    let msg = result?.message || fallback;

    if (result?.data && typeof result.data === "object") {
        const firstMsg = Object.values(result.data).find(
            (v) => typeof v === "string" && v.trim() !== ""
        );
        if (firstMsg) msg = firstMsg;
    }

    return msg;
}

async function apiRequest(url, options) {
    const token = getAccessToken();
    if (!token) {
        handleUnauthorized();
        return { ok: false, status: 401, result: null };
    }

    const headers = Object.assign({}, options?.headers || {}, {
        Authorization: `Bearer ${token}`
    });

    const fetchOptions = Object.assign({}, options, { headers });

    const response = await fetch(url, fetchOptions);

    if (response.status === 401) {
        handleUnauthorized();
        return { ok: false, status: 401, result: null };
    }

    const result = await response.json().catch(() => null);
    return { ok: response.ok, status: response.status, result };
}

async function LoadProfile() {
    const res = await apiRequest("/api/v1/users/me/profile", { method: "GET" });
    if (!res.ok) {
        if (res.status !== 401) console.error("프로필 조회 실패", res.status);
        return;
    }

    currentProfile = res.result?.data || null;

    const { nicknameText } = getEls();
    if (!nicknameText) return;

    const nickname =
        currentProfile ? (currentProfile.nickName || currentProfile.nickname || "") : "";
    nicknameText.textContent = nickname ? `${nickname}님` : "";
}

async function changeNickname() {
    const { modalBody } = getEls();
    const input = modalBody ? modalBody.querySelector("#newNicknameInput") : null;
    const errorMessage = modalBody ? modalBody.querySelector("#errorMessage") : null;

    const nickname = input ? input.value.trim() : "";

    if (errorMessage) {
        errorMessage.textContent = "";
        errorMessage.classList.add("hidden");
    }

    if (!nickname) {
        if (errorMessage) {
            errorMessage.textContent = "닉네임을 입력하세요.";
            errorMessage.classList.remove("hidden");
        } else {
            alert("닉네임을 입력하세요.");
        }
        return false;
    }

    try {
        const res = await apiRequest("/api/v1/users/me/nickname", {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ newNickName: nickname })
        });

        if (!res.ok) {
            const msg =
                res.result?.message ||
                (typeof res.result === "string" ? res.result : "") ||
                "닉네임을 확인해주세요.";

            if (errorMessage) {
                errorMessage.textContent = msg;
                errorMessage.classList.remove("hidden");
            } else {
                alert(msg);
            }
            return false;
        }

        alert("닉네임이 변경되었습니다.");

        const { nicknameText } = getEls();
        if (nicknameText) nicknameText.textContent = `${nickname}님`;

        return true;
    } catch (e) {
        console.error(e);
        if (errorMessage) {
            errorMessage.textContent = "요청 중 오류가 발생했습니다.";
            errorMessage.classList.remove("hidden");
        } else {
            alert("요청 중 오류가 발생했습니다.");
        }
        return false;
    }
}

async function changePassword(oldPassword, newPassword) {
    try {
        const res = await apiRequest("/api/v1/users/me/password", {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ oldPassword, newPassword })
        });

        if (!res.ok) {
            const msg = extractErrorMessage(res.result, "요청 값이 올바르지 않습니다.");
            alert(msg);
            return false;
        }

        alert("비밀번호가 변경되었습니다.");
        return true;
    } catch (e) {
        console.error("비밀번호 변경 오류", e);
        alert("서버 오류가 발생했습니다.");
        return false;
    }
}

async function withdrawal() {
    if (!confirm("정말 회원 탈퇴하시겠습니까?")) return false;

    try {
        const res = await apiRequest("/api/v1/users/me/delete", {
            method: "PATCH",
            credentials: "include"
        });

        // 401은 apiRequest 내부에서 handleUnauthorized()가 이미 alert/처리하므로 여기서 또 alert 하지 않음
        if (res.status === 401) {
            return false;
        }

        if (!res.ok) {
            const msg = extractErrorMessage(res.result, "요청 값이 올바르지 않습니다.");
            alert(msg);
            return false;
        }

        alert("회원 탈퇴가 완료되었습니다.");
        localStorage.removeItem("accessToken");
        window.location.href = "/login";
        return true;
    } catch (e) {
        console.error("회원 탈퇴 오류", e);
        alert("서버 오류가 발생했습니다.");
        return false;
    }
}

function bindModalOpenButtons() {
    const openButtons = document.querySelectorAll("[data-modal]");
    openButtons.forEach((btn) => {
        btn.addEventListener("click", () => {
            const key = btn.getAttribute("data-modal");
            if (!key) return;

            renderTemplate(key);
            setModalUiByKey(key);
            showModal();
        });
    });
}

function bindModalCloseEvents() {
    const { overlay, closeBtn, cancelBtn } = getEls();

    if (closeBtn) closeBtn.addEventListener("click", closeModal);
    if (cancelBtn) cancelBtn.addEventListener("click", closeModal);

    if (overlay) {
        overlay.addEventListener("click", (e) => {
            if (e.target === overlay) closeModal();
        });
    }

    window.addEventListener("keydown", (e) => {
        const { overlay: ov } = getEls();
        if (ov && !ov.hidden && e.key === "Escape") closeModal();
    });
}

function bindModalConfirm() {
    const { confirmBtn } = getEls();
    if (!confirmBtn) return;

    confirmBtn.addEventListener("click", async () => {
        const { modalTitle, modalBody } = getEls();
        const title = modalTitle ? modalTitle.textContent || "" : "";

        if (title.includes("닉네임")) {
            const ok = await changeNickname();
            if (ok) closeModal();
            return;
        }

        if (title.includes("비밀번호")) {
            const cur = modalBody?.querySelector("#currentPasswordInput")?.value || "";
            const nw = modalBody?.querySelector("#newPasswordInput")?.value || "";
            const nw2 = modalBody?.querySelector("#newPasswordConfirmInput")?.value || "";

            if (!cur || !nw || !nw2) {
                alert("모든 항목을 입력하세요.");
                return;
            }

            if (nw !== nw2) {
                alert("새 비밀번호가 일치하지 않습니다.");
                return;
            }

            const ok = await changePassword(cur, nw);
            if (ok) closeModal();
            return;
        }

        if (title.includes("회원 탈퇴")) {
            const chk = modalBody?.querySelector("#withdrawAgreeChk");
            if (!chk || !chk.checked) return;

            const ok = await withdrawal();
            if (ok) closeModal();
            return;
        }

        closeModal();
    });
}

window.addEventListener("load", () => {
    LoadProfile();
    bindModalOpenButtons();
    bindModalCloseEvents();
    bindModalConfirm();
});
