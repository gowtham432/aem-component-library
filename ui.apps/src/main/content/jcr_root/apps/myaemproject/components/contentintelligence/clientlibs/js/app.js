/**
 * Content Intelligence – Chat App
 * Vanilla JS, no framework dependencies.
 */
(function () {
  'use strict';

  var API_CHAT     = '/bin/api/contentIntelligence';
  var API_PAGES    = '/bin/api/pageList';

  var chat             = document.getElementById('ciChat');
  var input            = document.getElementById('ciInput');
  var sendBtn          = document.getElementById('ciSendBtn');
  var pageSelect       = document.getElementById('ciPageSelect');
  var pageLoadingEl    = document.getElementById('ciPageLoading');
  var chipsContainer   = document.getElementById('ciChips');

  var conversationHistory = [];
  var isWaiting           = false;

  // ── Initialise ──────────────────────────────────────────────

  function init() {
    loadPageList();
    bindEvents();
  }

  // ── Page List ───────────────────────────────────────────────

  function loadPageList() {
    showPageLoading(true);
    fetch(API_PAGES, { credentials: 'same-origin' })
      .then(function (r) { return r.json(); })
      .then(function (data) {
        showPageLoading(false);
        if (data.pages && data.pages.length) {
          data.pages.forEach(function (page) {
            var opt = document.createElement('option');
            opt.value = page.path;
            opt.textContent = page.title + '  (' + page.path + ')';
            pageSelect.appendChild(opt);
          });
        }
      })
      .catch(function (err) {
        showPageLoading(false);
        console.warn('ContentIntelligence: failed to load page list', err);
      });
  }

  function showPageLoading(visible) {
    if (pageLoadingEl) {
      pageLoadingEl.style.display = visible ? 'inline' : 'none';
    }
  }

  // ── Events ──────────────────────────────────────────────────

  function bindEvents() {
    sendBtn.addEventListener('click', handleSend);

    input.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    });

    input.addEventListener('input', function () {
      sendBtn.disabled = input.value.trim() === '' || isWaiting;
      autoResizeTextarea();
    });

    if (chipsContainer) {
      chipsContainer.addEventListener('click', function (e) {
        var chip = e.target.closest('.ci-chip');
        if (chip) {
          var question = chip.dataset.question;
          if (question) {
            input.value = question;
            input.dispatchEvent(new Event('input'));
            handleSend();
          }
        }
      });
    }
  }

  function autoResizeTextarea() {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 120) + 'px';
  }

  // ── Send ────────────────────────────────────────────────────

  function handleSend() {
    var question = input.value.trim();
    if (!question || isWaiting) return;

    var pagePath = pageSelect.value || '';

    appendMessage('user', question);
    input.value = '';
    input.style.height = 'auto';
    sendBtn.disabled = true;
    setWaiting(true);

    var typingId = showTypingIndicator();

    var body = {
      question: question,
      pagePath: pagePath,
      conversationHistory: conversationHistory.slice()
    };

    fetch(API_CHAT, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
      .then(function (r) { return r.json(); })
      .then(function (data) {
        removeTypingIndicator(typingId);
        setWaiting(false);

        if (data.error) {
          appendMessage('assistant', data.error, null, true);
        } else {
          appendMessage('assistant', data.answer, data.sources);
          conversationHistory.push({ role: 'user',      content: question });
          conversationHistory.push({ role: 'assistant', content: data.answer });
          // Keep history bounded to last 10 turns
          if (conversationHistory.length > 20) {
            conversationHistory = conversationHistory.slice(-20);
          }
        }
      })
      .catch(function (err) {
        removeTypingIndicator(typingId);
        setWaiting(false);
        appendMessage('assistant',
          'Sorry, I could not reach the server. Please check your connection and try again.',
          null, true);
        console.error('ContentIntelligence fetch error:', err);
      });
  }

  // ── Render messages ─────────────────────────────────────────

  function appendMessage(role, text, sources, isError) {
    var wrapper = document.createElement('div');
    wrapper.className = 'ci-message ci-message--' + role +
      (isError ? ' ci-message--error' : '');

    var avatar = document.createElement('div');
    avatar.className = 'ci-message__avatar';
    avatar.textContent = role === 'user' ? 'You' : 'AI';

    var bubble = document.createElement('div');
    bubble.className = 'ci-message__bubble';
    bubble.innerHTML = formatText(text);

    if (sources && sources.length > 0) {
      var sourcesEl = document.createElement('div');
      sourcesEl.className = 'ci-message__sources';
      sourcesEl.innerHTML = '<strong>Sources</strong><ul>' +
        sources.map(function (s) {
          return '<li>' + escapeHtml(s) + '</li>';
        }).join('') + '</ul>';
      bubble.appendChild(sourcesEl);
    }

    wrapper.appendChild(avatar);
    wrapper.appendChild(bubble);
    chat.appendChild(wrapper);
    scrollToBottom();
  }

  function showTypingIndicator() {
    var id = 'ci-typing-' + Date.now();
    var wrapper = document.createElement('div');
    wrapper.className = 'ci-message ci-typing';
    wrapper.id = id;

    var avatar = document.createElement('div');
    avatar.className = 'ci-message__avatar';
    avatar.textContent = 'AI';

    var dots = document.createElement('div');
    dots.className = 'ci-typing__dots';
    dots.innerHTML =
      '<div class="ci-typing__dot"></div>' +
      '<div class="ci-typing__dot"></div>' +
      '<div class="ci-typing__dot"></div>';

    wrapper.appendChild(avatar);
    wrapper.appendChild(dots);
    chat.appendChild(wrapper);
    scrollToBottom();
    return id;
  }

  function removeTypingIndicator(id) {
    var el = document.getElementById(id);
    if (el) el.remove();
  }

  // ── Helpers ─────────────────────────────────────────────────

  function setWaiting(waiting) {
    isWaiting = waiting;
    input.disabled = waiting;
    sendBtn.disabled = waiting || input.value.trim() === '';
  }

  function scrollToBottom() {
    chat.scrollTop = chat.scrollHeight;
  }

  /**
   * Very lightweight Markdown-ish formatter:
   * - **bold**  -> <strong>
   * - *italic*  -> <em>
   * - `code`    -> <code>
   * - newlines  -> <br> / <p>
   * - bullet lines (- item) -> <ul><li>
   */
  function formatText(text) {
    if (!text) return '';

    // Split into paragraphs on double newline
    var paragraphs = text.split(/\n{2,}/);
    return paragraphs.map(function (para) {
      // Detect bullet list
      var lines = para.split('\n');
      var isList = lines.every(function (l) { return /^\s*[-*]\s/.test(l) || l.trim() === ''; });
      if (isList) {
        var items = lines
          .filter(function (l) { return l.trim() !== ''; })
          .map(function (l) { return '<li>' + inlineFormat(l.replace(/^\s*[-*]\s+/, '')) + '</li>'; });
        return '<ul>' + items.join('') + '</ul>';
      }

      var lineHtml = lines.map(function (l) { return inlineFormat(l); }).join('<br>');
      return '<p>' + lineHtml + '</p>';
    }).join('');
  }

  function inlineFormat(text) {
    text = escapeHtml(text);
    text = text.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    text = text.replace(/\*(.+?)\*/g,     '<em>$1</em>');
    text = text.replace(/`(.+?)`/g,       '<code>$1</code>');
    return text;
  }

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  // ── Boot ────────────────────────────────────────────────────

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

}());
