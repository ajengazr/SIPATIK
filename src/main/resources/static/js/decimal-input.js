// Mengizinkan input desimal dengan koma atau titik dan normalisasi ke titik saat submit
(function () {
  function sanitizeDecimal(value) {
    if (value == null) return '';
    var str = String(value).trim();
    // Hanya angka, koma, titik, dan minus
    str = str.replace(/[^0-9,.-]/g, '');
    // Jika ada lebih dari satu koma/titik, pertahankan pemisah desimal terakhir
    var lastComma = str.lastIndexOf(',');
    var lastDot = str.lastIndexOf('.');
    var sep = Math.max(lastComma, lastDot);
    var integerPart = str;
    var fractionalPart = '';
    if (sep > 0) {
      integerPart = str.slice(0, sep).replace(/[.,]/g, '');
      fractionalPart = str.slice(sep + 1).replace(/[.,]/g, '');
    } else {
      integerPart = str.replace(/[.,]/g, '');
    }
    if (fractionalPart.length > 0) {
      return integerPart + '.' + fractionalPart;
    }
    return integerPart;
  }

  function onInput(e) {
    var v = e.target.value;
    // Biarkan user mengetik bebas, tapi hilangkan karakter ilegal on the fly
    var cleaned = v.replace(/[^0-9,.-]/g, '');
    e.target.value = cleaned;
  }

  function normalizeOnSubmit(form) {
    var fields = form.querySelectorAll('input.decimal-input');
    fields.forEach(function (f) {
      f.value = sanitizeDecimal(f.value);
    });
  }

  function init() {
    var fields = document.querySelectorAll('input.decimal-input');
    fields.forEach(function (el) {
      el.addEventListener('input', onInput);
    });

    // Normalisasi saat submit form
    var forms = document.querySelectorAll('form');
    forms.forEach(function (form) {
      form.addEventListener('submit', function () {
        normalizeOnSubmit(form);
      });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();


