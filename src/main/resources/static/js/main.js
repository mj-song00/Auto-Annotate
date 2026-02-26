let currentFolderId = null;
let documentsByTarget = {};

let currentFolderPage = 1;
const folderPageSize = 10;

let folderTotalPages = 0;
let folderMenuDocClickBound = false;

function bindLogoutButton() {
    const logoutBtn = document.querySelector("header button");
    if (!logoutBtn) return;

    logoutBtn.addEventListener("click", async () => {
        await fetch("/api/v1/auth/logout", {
            method: "POST",
            credentials: "include"
        });

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

        const uploadCenter = document.querySelector(".upload-center");
        const resultArea = document.querySelector(".result-area");
        if (uploadCenter) uploadCenter.style.display = "none";
        if (resultArea) resultArea.style.display = "block";

        currentFolderPage = 1;
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
        const response = await fetch(`/api/v1/folder?page=${currentFolderPage}&size=${folderPageSize}`, {
            method: "GET",
            headers: { Authorization: `Bearer ${token}` }
        });

        if (response.status === 401) {
            handleUnauthorized();
            return;
        }

        if (!response.ok) {
            console.error("폴더 조회 실패", response.status);
            return;
        }

        const result = await response.json();

        const data = result.data || {};
        const folders = data.content || [];

        const number = typeof data.number === "number" ? data.number : 0;
        const totalPages = typeof data.totalPages === "number" ? data.totalPages : 0;

        folderTotalPages = totalPages;
        currentFolderPage = number + 1;

        const prevBtn = document.getElementById("folderPrev");
        const nextBtn = document.getElementById("folderNext");
        const pageInfo = document.getElementById("folderPageInfo");

        if (pageInfo) pageInfo.textContent = `${currentFolderPage} / ${totalPages}`;
        if (prevBtn) prevBtn.disabled = number <= 0;
        if (nextBtn) nextBtn.disabled = totalPages === 0 || number >= totalPages - 1;

        const folderList = document.getElementById("folderList");
        if (!folderList) return;

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

        if (!folderMenuDocClickBound) {
            document.addEventListener("click", () => {
                document.querySelectorAll(".folder-menu").forEach(m => {
                    m.style.display = "none";
                });
            });
            folderMenuDocClickBound = true;
        }

        if (folders.length === 0) {
            currentFolderId = null;

            document.querySelectorAll("#folderList li").forEach(li => li.classList.remove("active"));

            const resultArea = document.querySelector(".result-area");
            const uploadCenter = document.querySelector(".upload-center");
            if (resultArea) resultArea.style.display = "none";
            if (uploadCenter) uploadCenter.style.display = "flex";
            return;
        }

        if (folders.length > 0 && !currentFolderId && currentFolderPage === 1) {
            loadFolder(folders[0].id);
        }
    } catch (e) {
        console.error(e);
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
    if (!token) {
        handleUnauthorized();
        return;
    }

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
    if (!token) {
        handleUnauthorized();
        return;
    }

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

        const resultArea = document.querySelector(".result-area");
        const uploadCenter = document.querySelector(".upload-center");
        if (resultArea) resultArea.style.display = "none";
        if (uploadCenter) uploadCenter.style.display = "flex";
    }

    await getFolders();
}

function loadFolder(folderId) {
    const token = getAccessToken();
    if (!token) {
        handleUnauthorized();
        return;
    }

    currentFolderId = folderId;
    documentsByTarget = {};

    document.querySelectorAll("#folderList li").forEach(li => li.classList.remove("active"));

    const clickedLi = [...document.querySelectorAll("#folderList li")]
        .find(li => li.dataset.folderId === folderId);

    if (clickedLi) clickedLi.classList.add("active");

    const uploadCenter = document.querySelector(".upload-center");
    const resultArea = document.querySelector(".result-area");
    if (uploadCenter) uploadCenter.style.display = "none";
    if (resultArea) resultArea.style.display = "block";

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
                return null;
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
    if (!token) {
        handleUnauthorized();
        return;
    }

    fetch(`/api/v1/document/${documentId}/excel?condition=${conditionNum}`, {
        headers: { Authorization: `Bearer ${token}` }
    })
        .then(response => {
            if (response.status === 401) {
                handleUnauthorized();
                return null;
            }

            if (!response.ok) {
                alert("다운로드 실패");
                return null;
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

window.addEventListener("load", async () => {
    const ok = await checkAuth();
    if (!ok) return;

    bindLogoutButton();
    await getFolders();

    const uploadBtn = document.getElementById("uploadBtn");
    const fileInput = document.getElementById("fileInput");
    const folderInput = document.getElementById("folderName");
    const newUploadBtn = document.getElementById("newUploadBtn");

    const prevBtn = document.getElementById("folderPrev");
    const nextBtn = document.getElementById("folderNext");

    if (prevBtn) {
        prevBtn.addEventListener("click", async () => {
            if (currentFolderPage <= 1) return;
            currentFolderPage -= 1;
            await getFolders();
        });
    }

    if (nextBtn) {
        nextBtn.addEventListener("click", async () => {
            if (folderTotalPages > 0 && currentFolderPage >= folderTotalPages) return;
            currentFolderPage += 1;
            await getFolders();
        });
    }

    if (newUploadBtn) {
        newUploadBtn.addEventListener("click", () => {
            currentFolderId = null;
            documentsByTarget = {};

            document.querySelectorAll("#folderList li").forEach(li => li.classList.remove("active"));

            const resultArea = document.querySelector(".result-area");
            const uploadCenter = document.querySelector(".upload-center");
            if (resultArea) resultArea.style.display = "none";
            if (uploadCenter) uploadCenter.style.display = "flex";

            if (folderInput) folderInput.value = "";
            if (fileInput) fileInput.value = "";
        });
    }

    if (uploadBtn && fileInput && folderInput) {
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
    }

    await LoadProfile();

    const dl0 = document.getElementById("dl0");
    const dl1 = document.getElementById("dl1");
    const dl2 = document.getElementById("dl2");
    const dl3 = document.getElementById("dl3");

    if (dl0) dl0.addEventListener("click", () => download(0));
    if (dl1) dl1.addEventListener("click", () => download(1));
    if (dl2) dl2.addEventListener("click", () => download(2));
    if (dl3) dl3.addEventListener("click", () => download(3));
});
