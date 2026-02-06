function getAccessToken() {
    return localStorage.getItem("accessToken");
}

function handleUnauthorized() {
    alert("인증이 만료되었습니다.");
    localStorage.removeItem("accessToken");
    window.location.href = "/login";
}

async function checkAuth() {
    const token = getAccessToken();
    if (!token || token.trim() === "") {
        alert("로그인이 필요합니다.");
        window.location.href = "/login";
        return;
    }
}

async function LoadProfile() {
    const token = getAccessToken();
    if (!token) {
        handleUnauthorized();
        return;
    }

    const response = await fetch("/api/v1/users/me/profile", {
        headers: { Authorization: `Bearer ${token}` }
    });

    if (response.status === 401) {
        handleUnauthorized();
        return;
    }

    if (!response.ok) {
        console.error("프로필 조회 실패", response.status);
        return;
    }
}

function bindLogoutButton() {
    const logoutBtn = document.querySelector("header button");
    if (!logoutBtn) return;

    logoutBtn.addEventListener("click", () => {
        localStorage.removeItem("accessToken");
        window.location.href = "/login";
    });
}

async function uploadFiles(files, folderName) {
    const token = getAccessToken();
    if (!token) {
        handleUnauthorized();
        return;
    }

    const formData = new FormData();
    for (const file of files) {
        formData.append("documents", file);
    }

    formData.append(
        "saveFolderRequest",
        new Blob([JSON.stringify({ name: folderName })], { type: "application/json" })
    );

    try {
        const response = await fetch("/api/v1/document/upload", {
            method: "POST",
            headers: { Authorization: `Bearer ${token}` },
            body: formData
        });

        if (response.status === 401) {
            handleUnauthorized();
            return;
        }

        if (!response.ok) {
            console.error("업로드 실패", response.status);
            return;
        }

        console.log("업로드 성공");

        document.querySelector(".upload-center").style.display = "none";
        document.querySelector(".result-area").style.display = "block";

        await getFolders();

    } catch (e) {
        console.error("업로드 오류", e);
    }
}

async function getFolders() {
    const token = getAccessToken();
    if (!token) {
        handleUnauthorized();
        return;
    }

    try {
        const response = await fetch("/api/v1/folder", {
            method: "GET",
            headers: { Authorization: `Bearer ${token}` }
        });

        if (response.status === 401) {
            handleUnauthorized();
            return;
        }

        const result = await response.json();
        const folders = result.data || [];

        const folderList = document.getElementById("folderList");
        folderList.innerHTML = "";

        folders.forEach(folder => {
            const li = document.createElement("li");
            li.className = "folder-item";
            li.dataset.folderId = folder.id;

            const nameSpan = document.createElement("span");
            nameSpan.textContent = folder.name;
            nameSpan.onclick = () => loadFolder(folder.id);

            const actions = document.createElement("span");
            actions.className = "folder-actions";
            actions.textContent = "⋯";

            const menu = document.createElement("div");
            menu.className = "folder-menu";

            menu.addEventListener("click", (e) => {
                e.stopPropagation();
            });

            const editItem = document.createElement("div");
            editItem.textContent = "수정";
            editItem.onclick = (e) => {
                e.stopPropagation();
                closeAllMenus();
                editFolder(folder.id, folder.name, li);
            };

            const deleteItem = document.createElement("div");
            deleteItem.textContent = "삭제";
            deleteItem.onclick = (e) => {
                e.stopPropagation();
                closeAllMenus();
                deleteFolder(folder.id);
            };

            menu.appendChild(editItem);
            menu.appendChild(deleteItem);

            actions.onclick = (e) => {
                e.stopPropagation();
                const isOpen = menu.style.display === "block";
                closeAllMenus();
                menu.style.display = isOpen ? "none" : "block";
            };

            li.appendChild(nameSpan);
            li.appendChild(actions);
            li.appendChild(menu);
            folderList.appendChild(li);
        });

        document.addEventListener("click", () => {
            document.querySelectorAll(".folder-menu").forEach(m => {
                m.style.display = "none";
            });
        });

        if (folders.length === 0) {
            currentFolderId = null;

            document.querySelectorAll("#folderList li").forEach(li => {
                li.classList.remove("active");
            });

            document.querySelector(".result-area").style.display = "none";
            document.querySelector(".upload-center").style.display = "flex";
            return;
        }

        if (folders.length > 0 && !currentFolderId) {
            loadFolder(folders[0].id);
        }

    } catch (e) {
        console.log(e);
    }
}

function closeAllMenus() {
    document.querySelectorAll(".folder-menu").forEach(menu => {
        menu.style.display = "none";
    });
}

function editFolder(folderId, currentName, li) {
    const nameSpan = li.querySelector("span");
    const actions = li.querySelector(".folder-actions");

    const input = document.createElement("input");
    input.type = "text";
    input.value = currentName;
    input.style.width = "100%";
    input.style.boxSizing = "border-box";

    nameSpan.style.display = "none";
    actions.style.display = "none";

    li.insertBefore(input, actions);
    input.focus();
    input.select();

    const cleanup = () => {
        input.remove();
        nameSpan.style.display = "";
        actions.style.display = "";
    };

    const submit = async () => {
        const newName = input.value.trim();
        if (!newName || newName === currentName) {
            cleanup();
            return;
        }

        await updateFolderName(folderId, newName);
        nameSpan.textContent = newName;
        cleanup();
    };

    input.addEventListener("keydown", (e) => {
        if (e.key === "Enter") submit();
        if (e.key === "Escape") cleanup();
    });

    input.addEventListener("blur", submit);
}

async function updateFolderName(folderId, name) {
    const token = getAccessToken();

    const response = await fetch(`/api/v1/folder/${folderId}`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({ name })
    });

    if (response.status === 401) {
        handleUnauthorized();
        return;
    }

    if (!response.ok) {
        alert("폴더 수정 실패");
        return;
    }

    await getFolders();
}

async function deleteFolder(folderId) {
    const token = getAccessToken();
    if (!confirm("폴더를 삭제하시겠습니까?")) return;

    const response = await fetch(`/api/v1/folder/${folderId}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` }
    });

    if (response.status === 401) {
        handleUnauthorized();
        return;
    }

    if (!response.ok) {
        alert("폴더 삭제 실패");
        return;
    }

    if (currentFolderId === folderId) {
        currentFolderId = null;
        documentsByTarget = {};
        document.querySelector(".result-area").style.display = "none";
        document.querySelector(".upload-center").style.display = "flex";
    }

    await getFolders();
}

let currentFolderId = null;
let documentsByTarget = {};

function loadFolder(folderId) {
    const token = getAccessToken();
    currentFolderId = folderId;

    documentsByTarget = {};

    document.querySelectorAll("#folderList li").forEach(li => {
        li.classList.remove("active");
    });

    const clickedLi = [...document.querySelectorAll("#folderList li")]
        .find(li => li.dataset.folderId === folderId);

    if (clickedLi) {
        clickedLi.classList.add("active");
    }

    document.querySelector(".upload-center").style.display = "none";
    document.querySelector(".result-area").style.display = "block";

    document.querySelectorAll(".download-buttons button").forEach(btn => {
        btn.disabled = true;
    });

    fetch(`/api/v1/folder/${folderId}/documents`, {
        method: "GET",
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`
        }
    })
        .then(res => {
            if (res.status === 401) {
                handleUnauthorized();
                return;
            }
            if (!res.ok) throw new Error("폴더 문서 목록 조회 실패");
            return res.json();
        })
        .then(result => {
            if (!result) return;

            const documents = result.data || [];

            documents.forEach(d => {
                if (d && d.target && d.documentId) {
                    documentsByTarget[d.target] = d.documentId;
                }
            });

            document.querySelectorAll(".download-buttons button").forEach(btn => {
                const condition = Number(btn.dataset.condition);
                const conditionToTarget = {
                    0: "VISIT_SUMMARY",
                    1: "DRUG_SUMMARY",
                    2: "TREATMENT_DETAIL",
                    3: "PRESCRIPTION"
                };
                const target = conditionToTarget[condition];

                btn.disabled = !documentsByTarget[target];
            });
        })
        .catch(err => {
            console.error(err);
        });
}

function download(condition) {
    if (!currentFolderId) {
        alert("폴더를 먼저 선택해주세요.");
        return;
    }

    const conditionNum = Number(condition);

    const conditionToTarget = {
        0: "VISIT_SUMMARY",
        1: "DRUG_SUMMARY",
        2: "TREATMENT_DETAIL",
        3: "PRESCRIPTION"
    };

    const target = conditionToTarget[conditionNum];
    const documentId = documentsByTarget[target];

    if (!documentId) {
        alert("문서가 존재하지 않습니다.");
        return;
    }

    const token = getAccessToken();

    fetch(`/api/v1/document/${documentId}/excel?condition=${conditionNum}`, {
        headers: {
            Authorization: `Bearer ${token}`
        }
    })
        .then(response => {
            if (response.status === 401) {
                handleUnauthorized();
                return;
            }

            if (!response.ok) {
                alert("다운로드 실패");
                return;
            }

            return response.blob();
        })
        .then(blob => {
            if (!blob) return;

            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = `${conditionNum}.xlsx`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
        });
}

window.addEventListener("load", () => {
    checkAuth();
    LoadProfile();
    bindLogoutButton();
    getFolders();

    const uploadBtn = document.getElementById("uploadBtn");
    const fileInput = document.getElementById("fileInput");
    const folderInput = document.getElementById("folderName");
    const newUploadBtn = document.getElementById("newUploadBtn");

    newUploadBtn.addEventListener("click", () => {
        currentFolderId = null;
        documentsByTarget = {};

        document.querySelectorAll("#folderList li").forEach(li => {
            li.classList.remove("active");
        });

        document.querySelector(".result-area").style.display = "none";
        document.querySelector(".upload-center").style.display = "flex";

        folderInput.value = "";
        fileInput.value = "";
    });

    uploadBtn.addEventListener("click", () => {
        const folderName = folderInput.value.trim();
        if (!folderName) {
            alert("폴더 이름을 입력해주세요.");
            return;
        }
        fileInput.click();
    });

    fileInput.addEventListener("change", () => {
        const folderName = folderInput.value.trim();
        if (!folderName) {
            alert("폴더 이름을 입력해주세요.");
            fileInput.value = "";
            return;
        }
        if (!fileInput.files.length) return;
        uploadFiles(fileInput.files, folderName);
        fileInput.value = "";
    });

    document.getElementById("dl0").addEventListener("click", () => download(0));
    document.getElementById("dl1").addEventListener("click", () => download(1));
    document.getElementById("dl2").addEventListener("click", () => download(2));
    document.getElementById("dl3").addEventListener("click", () => download(3));
});
