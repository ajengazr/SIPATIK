// Formatter tanpa prefix "Rp" untuk elemen dengan class "format-rupiah-norp"
(function () {
  function toNumber(value) {
    if (value == null) return 0;
    if (typeof value === 'number') return value;
    // buang semua karakter non digit dan non koma/titik
    var cleaned = String(value).replace(/[^0-9.,-]/g, '').replace(/,/g, '.');
    var num = parseFloat(cleaned);
    return isNaN(num) ? 0 : num;
  }

  function formatIdLocale(num) {
    try {
      return new Intl.NumberFormat('id-ID', { maximumFractionDigits: 2 }).format(num);
    } catch (e) {
      // fallback
      return (Math.round(num * 100) / 100).toString();
    }
  }

  function formatElement(el) {
    var raw = el.getAttribute('data-value') || el.textContent || '';
    var num = toNumber(raw);
    el.textContent = formatIdLocale(num);
    el.setAttribute('data-value', num);
  }

  function init() {
    var nodes = document.querySelectorAll('.format-rupiah-norp');
    nodes.forEach(formatElement);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();


