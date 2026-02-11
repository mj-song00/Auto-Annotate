document.getElementById("loginForm").addEventListener("submit", async function (e) {
    e.preventDefault();

    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;
    const errorMessage = document.getElementById("errorMessage");

    errorMessage.classList.add("hidden");

    try {
        const response = await fetch("/api/v1/users/auth/sign-in", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                email: email,
                password: password
            })

        });
        if (!response.ok) {
            throw new Error("LOGIN_FAILED");
        }

        const accessToken = await response.text();
        localStorage.setItem("accessToken", accessToken);

        window.location.href = "/main";

    } catch (e) {
        console.log(e.message)
        errorMessage.textContent = "이메일 또는 비밀번호가 올바르지 않습니다.";
        errorMessage.classList.remove("hidden");
    }
});