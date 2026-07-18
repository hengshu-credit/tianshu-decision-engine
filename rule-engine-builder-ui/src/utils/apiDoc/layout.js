export function renderResizeHandles() {
  return {
    nav: '<div class="resize-handle resize-handle-nav" data-resize="nav" role="separator" aria-orientation="vertical" aria-label="调整接口菜单宽度"></div>',
    runner: '<div class="resize-handle resize-handle-runner" data-resize="runner" role="separator" aria-orientation="vertical" aria-label="调整调试栏宽度"></div>'
  }
}

export function renderLayoutScript() {
  return `(function () {
  'use strict';
  var root = document.documentElement;
  var dragSide = '';

  function isCompact() { return window.matchMedia && window.matchMedia('(max-width: 1000px)').matches; }
  function setWidth(side, clientX) {
    if (side === 'nav') {
      var navWidth = Math.min(window.innerWidth * 0.2, Math.max(200, clientX));
      root.style.setProperty('--nav-width', navWidth + 'px');
    } else {
      var runnerWidth = Math.min(window.innerWidth * 0.4, Math.max(360, window.innerWidth - clientX));
      root.style.setProperty('--runner-width', runnerWidth + 'px');
    }
  }
  function stopDrag() {
    dragSide = '';
    document.body.classList.remove('is-resizing');
  }
  function activateEndpoint(endpointValue, shouldScroll, syncRunner) {
    var value = String(endpointValue == null ? '' : endpointValue);
    var activePanel = null;
    document.querySelectorAll('.endpoint-panel').forEach(function (panel) {
      var active = panel.getAttribute('data-endpoint-value') === value;
      panel.classList.toggle('active', active);
      if (active) activePanel = panel;
    });
    document.querySelectorAll('[data-endpoint-nav]').forEach(function (button) {
      button.classList.toggle('active', button.getAttribute('data-endpoint-nav') === value);
    });
    var endpointSelect = document.getElementById('runner-endpoint');
    if (syncRunner !== false && endpointSelect && endpointSelect.value !== value) {
      endpointSelect.value = value;
      endpointSelect.dispatchEvent(new Event('change', { bubbles: true }));
    }
    if (shouldScroll && activePanel) activePanel.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
  function initialize() {
    document.querySelectorAll('[data-resize]').forEach(function (handle) {
      handle.addEventListener('pointerdown', function (event) {
        if (isCompact()) return;
        dragSide = handle.getAttribute('data-resize');
        document.body.classList.add('is-resizing');
        if (handle.setPointerCapture) handle.setPointerCapture(event.pointerId);
        event.preventDefault();
      });
      handle.addEventListener('dblclick', function () {
        var side = handle.getAttribute('data-resize');
        setWidth(side, side === 'nav' ? 256 : window.innerWidth - 440);
      });
    });
    window.addEventListener('pointermove', function (event) { if (dragSide) setWidth(dragSide, event.clientX); });
    window.addEventListener('pointerup', stopDrag);
    window.addEventListener('pointercancel', stopDrag);
    document.querySelectorAll('[data-endpoint-nav]').forEach(function (button) {
      button.addEventListener('click', function () { activateEndpoint(button.getAttribute('data-endpoint-nav'), true, true); });
    });
    var endpointSelect = document.getElementById('runner-endpoint');
    if (endpointSelect) endpointSelect.addEventListener('change', function () { activateEndpoint(endpointSelect.value, false, false); });
    var first = document.querySelector('.endpoint-panel.active') || document.querySelector('.endpoint-panel');
    if (first) activateEndpoint(first.getAttribute('data-endpoint-value'), false, true);
    window.ApiDocLayout = { activateEndpoint: activateEndpoint };
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize); else initialize();
}());`
}
