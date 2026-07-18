import { escapeAttribute, escapeHtml } from './escape'

export function renderCodeEditor({ id, mode = 'text', value = '', rows = 10 }) {
  const editorId = escapeAttribute(id)
  return `<div class="code-editor" data-code-editor="${editorId}" data-editor-mode="${escapeAttribute(mode)}">
    <div class="code-editor-toolbar">
      <span class="code-editor-language">${escapeHtml(String(mode).toUpperCase())}</span>
      <button class="code-editor-action" type="button" data-editor-reset="${editorId}">重置样例</button>
      <button class="code-editor-action" type="button" data-editor-format="${editorId}">格式化</button>
    </div>
    <div class="code-editor-frame" style="--editor-rows:${Number(rows) || 10}">
      <pre class="code-editor-lines" aria-hidden="true">1</pre>
      <div class="code-editor-content">
        <pre class="code-editor-highlight" aria-hidden="true"><code></code></pre>
        <textarea id="${editorId}" class="code-editor-input" spellcheck="false" autocomplete="off" aria-describedby="${editorId}-error">${escapeHtml(value)}</textarea>
      </div>
    </div>
    <div id="${editorId}-error" class="code-editor-error" role="status" aria-live="polite"></div>
  </div>`
}

export function renderCodeEditorScript() {
  return `(function () {
  'use strict';
  var editors = {};

  function escapeMarkup(value) {
    return String(value == null ? '' : value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }
  function highlightJson(value) {
    var pattern = /("(?:\\\\u[a-fA-F0-9]{4}|\\\\[^u]|[^\\\\"])*"(?=\\s*:))|("(?:\\\\u[a-fA-F0-9]{4}|\\\\[^u]|[^\\\\"])*")|\\b(true|false)\\b|\\b(null)\\b|(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)/g;
    var output = '';
    var lastIndex = 0;
    var match;
    while ((match = pattern.exec(value)) !== null) {
      output += escapeMarkup(value.slice(lastIndex, match.index));
      var className = match[1] ? 'code-token-key' : match[2] ? 'code-token-string' : match[3] ? 'code-token-boolean' : match[4] ? 'code-token-null' : 'code-token-number';
      output += '<span class="' + className + '">' + escapeMarkup(match[0]) + '</span>';
      lastIndex = pattern.lastIndex;
    }
    return output + escapeMarkup(value.slice(lastIndex));
  }
  function jsonErrorLine(value, error) {
    var position = /position\\s+(\\d+)/i.exec(error && error.message ? error.message : '');
    if (!position) return '';
    return String(value.slice(0, Number(position[1])).split(/\\r?\\n/).length);
  }
  function validationError(editor) {
    var value = editor.input.value;
    if (editor.mode === 'json') {
      if (!value.trim()) return 'JSON 内容不能为空';
      try { JSON.parse(value); return ''; } catch (error) {
        var line = jsonErrorLine(value, error);
        return 'JSON 格式错误' + (line ? '（第 ' + line + ' 行）' : '') + '：' + error.message;
      }
    }
    if (editor.mode === 'kv') {
      var lines = value.split(/\\r?\\n/);
      for (var index = 0; index < lines.length; index += 1) {
        var lineValue = lines[index].trim();
        if (lineValue && lineValue.indexOf('=') <= 0) return '第 ' + (index + 1) + ' 行缺少 = 或参数名';
      }
    }
    return '';
  }
  function update(editor) {
    var value = editor.input.value;
    var lineCount = Math.max(1, value.split(/\\r?\\n/).length);
    editor.lines.textContent = Array.from({ length: lineCount }, function (_, index) { return index + 1; }).join('\\n');
    editor.highlight.innerHTML = editor.mode === 'json' ? highlightJson(value) : escapeMarkup(value);
    editor.error.textContent = validationError(editor);
    editor.root.classList.toggle('has-error', Boolean(editor.error.textContent));
  }
  function replaceSelection(input, replacement, offset) {
    var start = input.selectionStart;
    var end = input.selectionEnd;
    input.value = input.value.slice(0, start) + replacement + input.value.slice(end);
    input.selectionStart = input.selectionEnd = start + (offset == null ? replacement.length : offset);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }
  function handleKeydown(editor, event) {
    if (event.key === 'Tab') {
      event.preventDefault();
      replaceSelection(editor.input, '  ');
      return;
    }
    if (event.key === 'Enter' && editor.mode === 'json') {
      event.preventDefault();
      var before = editor.input.value.slice(0, editor.input.selectionStart);
      var currentLine = before.slice(before.lastIndexOf('\\n') + 1);
      var indent = (/^\\s*/.exec(currentLine) || [''])[0];
      if (/[\\[{]\\s*$/.test(currentLine)) indent += '  ';
      replaceSelection(editor.input, '\\n' + indent);
    }
  }
  function format(editor) {
    if (editor.mode === 'json') {
      try { editor.input.value = JSON.stringify(JSON.parse(editor.input.value), null, 2); } catch (error) { update(editor); return; }
    } else if (editor.mode === 'kv') {
      editor.input.value = editor.input.value.split(/\\r?\\n/).map(function (line) {
        var separator = line.indexOf('=');
        return separator < 0 ? line.trim() : line.slice(0, separator).trim() + '=' + line.slice(separator + 1).trim();
      }).filter(function (line) { return line; }).join('\\n');
    }
    update(editor);
  }
  function initialize() {
    document.querySelectorAll('[data-code-editor]').forEach(function (root) {
      var input = root.querySelector('.code-editor-input');
      var editor = {
        root: root,
        input: input,
        lines: root.querySelector('.code-editor-lines'),
        highlight: root.querySelector('.code-editor-highlight code'),
        error: root.querySelector('.code-editor-error'),
        mode: root.getAttribute('data-editor-mode') || 'text',
        initialValue: input.value
      };
      editors[input.id] = editor;
      input.addEventListener('input', function () { update(editor); });
      input.addEventListener('keydown', function (event) { handleKeydown(editor, event); });
      input.addEventListener('scroll', function () {
        editor.lines.scrollTop = input.scrollTop;
        editor.highlight.parentElement.scrollTop = input.scrollTop;
        editor.highlight.parentElement.scrollLeft = input.scrollLeft;
      });
      update(editor);
    });
    document.addEventListener('click', function (event) {
      var formatButton = event.target.closest('[data-editor-format]');
      if (formatButton && editors[formatButton.getAttribute('data-editor-format')]) format(editors[formatButton.getAttribute('data-editor-format')]);
      var resetButton = event.target.closest('[data-editor-reset]');
      if (resetButton && editors[resetButton.getAttribute('data-editor-reset')]) {
        var editor = editors[resetButton.getAttribute('data-editor-reset')];
        editor.input.value = editor.initialValue;
        update(editor);
      }
    });
    window.ApiDocEditors = {
      get: function (id) { return editors[id] ? editors[id].input.value : ''; },
      set: function (id, value, updateInitial) {
        if (!editors[id]) return;
        editors[id].input.value = value == null ? '' : String(value);
        if (updateInitial) editors[id].initialValue = editors[id].input.value;
        update(editors[id]);
      },
      validate: function (id) { return editors[id] ? validationError(editors[id]) : ''; }
    };
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize); else initialize();
}());`
}
