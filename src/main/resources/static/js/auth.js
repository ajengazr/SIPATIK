function loadNama() {
    const angkatan = document.getElementById("angkatan").value;
    const namaSelect = document.getElementById("nama");
    namaSelect.innerHTML = '<option value="">Loading...</option>';

    fetch('/api/nama-by-angkatan?angkatan=' + angkatan)
        .then(response => response.json())
        .then(data => {
            let options = '<option value="">Pilih Nama</option>';
            data.forEach(nama => {
                options += `<option value="${nama}">${nama}</option>`;
            });
            namaSelect.innerHTML = options;
        })
        .catch(error => {
            namaSelect.innerHTML = '<option value="">Gagal memuat nama</option>';
            console.error('Error:', error);
        });
}

const passwordSide = (loginPass, loginEye) => {
    const input = document.getElementById(loginPass);
    const iconEye = document.getElementById(loginEye);

    iconEye.addEventListener('click', () => {
        input.type === 'password' ? input.type = 'text' : input.type = 'password';
        iconEye.classList.toggle('ri-eye-fill');
        iconEye.classList.toggle('ri-eye-off-fill');
    })
}

passwordSide('password', 'loginPassword');

function goToLupaPassword() {
    const nama = document.getElementById('inputNama').value;
    const angkatan = document.getElementById('inputAngkatan').value;
    console.log('namanya : ' + nama)
    console.log('angkatan : ' + angkatan)

    const url = `/auth/form-otp?nama=${encodeURIComponent(nama)}&angkatan=${angkatan}`;
    window.location.href = url;
}

// ===== FORM OTP =====
function moveToNext(current) {
    const inputs = document.querySelectorAll('.otp-input');
    const index = Array.from(inputs).indexOf(current);
    if (current.value.length === 1 && index < inputs.length - 1) {
        inputs[index + 1].focus();
    }
}

document.querySelector('#otp-form').addEventListener('submit', function (e) {
    const otpInputs = Array.from(this.querySelectorAll('.otp-input'));
    const otp = otpInputs.map(input => input.value).join('');
    if (otp.length !== 6) {
        e.preventDefault(); // hentikan submit
        alert('Harap masukkan 6 digit OTP.');
        return;
    }

    // Set OTP ke input hidden
    document.getElementById('otp-hidden').value = otp;
});

// waktu hitung mundur 2 menit untuk resend otp
let countdown = 60; // 120 detik (2 menit)
const resendLink = document.getElementById("resendOtpLink");

const timerInterval = setInterval(() => {
    if (countdown <= 0) {
        clearInterval(timerInterval);
        resendLink.style.pointerEvents = "auto";
        resendLink.style.color = "#2563eb"; // warna biru Tailwind
        resendLink.style.cursor = "pointer";
        resendLink.innerText = "Kirim Ulang";
    } else {
        resendLink.innerText = `Kirim Ulang (${countdown} detik)`;
        countdown--;
    }
}, 1000);


function resendOtp() {
    const nama = document.getElementById('inputNama').value;
    const angkatan = document.getElementById('inputAngkatan').value;

    const url = `/auth/form-otp?nama=${encodeURIComponent(nama)}&angkatan=${angkatan}`;
    window.location.href = url;
}
