async function LoadProfile() {
    const token = getAccessToken();
    if (!token) {
        handleUnauthorized();
        return;
    }

    const response = await fetch("/api/v1/users/me/profile", {
        headers: {Authorization: `Bearer ${token}`}
    });

    if (response.status === 401) {
        handleUnauthorized();
        return;
    }

    if (!response.ok) {
        console.error("프로필 조회 실패", response.status);
        return;
    }

    const result = await response.json();
    currentProfile = result.data || null;

    const nicknameEl = document.getElementById("nicknameText");
    if (!nicknameEl) return;

    const nickname = currentProfile ? (currentProfile.nickName || "") : "";
    nicknameEl.textContent = nickname ? `${nickname}님` : "";
}

async function changeNickname() {
    const token = getAccessToken();
    if (!token) {
        handleUnauthorized();
        return false;
    }

    const modalBody = document.getElementById("modalBody");
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
        const response = await fetch(`/api/v1/users/me/nickname`, {
            method: "PATCH",
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ newNickName: nickname })
        });

        if (response.status === 401) {
            handleUnauthorized();
            return false;
        }

        if (!response.ok) {
            const msg = await response.text().catch(() => "");
            if (errorMessage) {
                errorMessage.textContent = msg || "닉네임을 확인해주세요.";
                errorMessage.classList.remove("hidden");
            } else {
                alert(msg || "닉네임을 확인해주세요.");
            }
            return false;
        }

        alert("닉네임이 변경되었습니다.");

        const nicknameEl = document.getElementById("nicknameText");
        if (nicknameEl) nicknameEl.textContent = `${nickname}님`;

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

window.addEventListener("load", () => {
    LoadProfile();


    const overlay = document.getElementById("modalOverlay");
    const modalTitle = document.getElementById("modalTitle");
    const modalBody = document.getElementById("modalBody");
    const closeBtn = document.getElementById("modalCloseBtn");
    const cancelBtn = document.getElementById("modalCancelBtn");
    const confirmBtn = document.getElementById("modalConfirmBtn");

    const openButtons = document.querySelectorAll("[data-modal]");

    openButtons.forEach((btn) => {
        btn.addEventListener("click", () => {
            const key = btn.getAttribute("data-modal");
            const tpl = document.getElementById(`tpl-${key}`);

            modalBody.innerHTML = "";
            if (tpl) {
                modalBody.appendChild(tpl.content.cloneNode(true));
            }

            if (key === "nickname") {
                modalTitle.textContent = "닉네임 변경";
                confirmBtn.textContent = "저장";
                confirmBtn.disabled = false;
            } else if (key === "password") {
                modalTitle.textContent = "비밀번호 변경";
                confirmBtn.textContent = "변경";
                confirmBtn.disabled = false;
            } else if (key === "withdraw") {
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

            overlay.hidden = false;
            document.body.style.overflow = "hidden";
        });
    });

    closeBtn.addEventListener("click", () => {
        overlay.hidden = true;
        document.body.style.overflow = "";
    });

    cancelBtn.addEventListener("click", () => {
        overlay.hidden = true;
        document.body.style.overflow = "";
    });

    overlay.addEventListener("click", (e) => {
        if (e.target === overlay) {
            overlay.hidden = true;
            document.body.style.overflow = "";
        }
    });

    window.addEventListener("keydown", (e) => {
        if (!overlay.hidden && e.key === "Escape") {
            overlay.hidden = true;
            document.body.style.overflow = "";
        }
    });

    confirmBtn.addEventListener("click", async () => {
        const title = modalTitle.textContent || "";

        if (title.includes("닉네임")) {
            const ok = await changeNickname();
            if (!ok) return;

            overlay.hidden = true;
            document.body.style.overflow = "";
            return;
        }

        if (title.includes("비밀번호")) {
            const cur = modalBody.querySelector("#currentPasswordInput")?.value || "";
            const nw = modalBody.querySelector("#newPasswordInput")?.value || "";
            const nw2 = modalBody.querySelector("#newPasswordConfirmInput")?.value || "";

            if (!cur || !nw || !nw2) {
                alert("모든 항목을 입력하세요.");
                return;
            }
            if (nw !== nw2) {
                alert("새 비밀번호가 일치하지 않습니다.");
                return;
            }
            alert("비밀번호 변경 요청 연결만 하면 됩니다.");

            overlay.hidden = true;
            document.body.style.overflow = "";
            return;
        }

        if (title.includes("회원 탈퇴")) {
            const chk = modalBody.querySelector("#withdrawAgreeChk");
            if (!chk || !chk.checked) return;
            if (!confirm("정말 탈퇴할까요?")) return;

            alert("회원 탈퇴 요청 연결만 하면 됩니다.");

            overlay.hidden = true;
            document.body.style.overflow = "";
            return;
        }

        overlay.hidden = true;
        document.body.style.overflow = "";
    });
});