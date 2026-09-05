// Mengizinkan input Rupiah dengan pemisah ribuan Indonesia maupun desimal,
// lalu menormalisasinya ke format BigDecimal saat submit.
(function () {
  function sanitizeDecimal(value) {
    if (value == null) return '';
    var str = String(value).trim();
    str = str.replace(/[^0-9,.-]/g, '');

    var negative = str.charAt(0) === '-';
    str = str.replace(/-/g, '');
    if (!str) return negative ? '-' : '';

    var lastComma = str.lastIndexOf(',');
    var lastDot = str.lastIndexOf('.');
    var hasComma = lastComma >= 0;
    var hasDot = lastDot >= 0;
    var decimalIndex = -1;

    if (hasComma && hasDot) {
      // Bila kedua simbol ada, simbol paling kanan adalah desimal dan sisanya ribuan.
      decimalIndex = Math.max(lastComma, lastDot);
    } else if (hasComma || hasDot) {
      var separator = hasComma ? ',' : '.';
      var groups = str.split(separator);
      var groupedThousands = groups.length > 1 && groups[0].length > 0 &&
        groups.slice(1).every(function (group) { return group.length === 3; });

      // 1.000 dan 1.000.000 adalah Rupiah bertanda ribuan. 1000,50 atau
      // 1000.00 tetap dibaca sebagai desimal karena bagian akhirnya bukan 3 digit.
      if (!groupedThousands) {
        decimalIndex = str.lastIndexOf(separator);
      }
    }

    var integerPart = str;
    var fractionalPart = '';
    if (decimalIndex >= 0) {
      integerPart = str.slice(0, decimalIndex).replace(/[.,]/g, '');
      fractionalPart = str.slice(decimalIndex + 1).replace(/[.,]/g, '');
    } else {
      integerPart = str.replace(/[.,]/g, '');
    }

    var result = (negative ? '-' : '') + (integerPart || '0');
    if (fractionalPart.length > 0) {
      result += '.' + fractionalPart;
    }
    return result;
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

  // Diekspos untuk smoke test browser tanpa mengubah perilaku form.
  window.SipatikDecimal = Object.freeze({ sanitize: sanitizeDecimal });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();


