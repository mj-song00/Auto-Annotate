document.getElementById("signupForm").addEventListener("submit", async function (e) {
    e.preventDefault();

    const email = document.getElementById("email").value;
    const nickName = document.getElementById("nickName").value;
    const password = document.getElementById("password").value;

    const emailError = document.getElementById("emailError");
    const nickNameError = document.getElementById("nickNameError");
    const passwordError = document.getElementById("passwordError");

    emailError.classList.add("hidden");
    nickNameError.classList.add("hidden");
    passwordError.classList.add("hidden");

    try {
        const response = await fetch("/api/v1/users/sign-up", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                email: email,
                nickName: nickName,
                password: password
            })
        });
        alert("회원가입이 완료되었습니다.\n로그인 페이지로 이동합니다.");


        if (!response.ok) {
            throw new Error("SIGNUP_FAILED");
        }

        window.location.href = "/login";

    } catch (e) {
        console.log(e.message)
        passwordError.textContent = "회원가입에 실패했습니다. 입력값을 확인해주세요.";
        passwordError.classList.remove("hidden");
    }
});