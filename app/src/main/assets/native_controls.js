(function () {
  if (document.getElementById('nativeGearOnly')) return;

  let moved = false;
  let sx = 0;
  let sy = 0;
  let ox = 0;
  let oy = 0;
  let playing = false;

  const cfg = {
    gap: 500,
    sp: { wordSlow: 0.62, spell: 0.72, wordNormal: 0.90, phrase: 0.62, sentence: 0.62, zh: 0.78 },
    ct: { wordSlow: 3, spell: 2, wordNormal: 3, meaning: 1, phrase: 1, sentence: 1, sentenceCn: 1 },
    txt: { text: '', lang: 'de', speed: 0.90, repeat: 1 }
  };

  try {
    const saved = JSON.parse(localStorage.nativeTrainConfig || '{}');
    if (saved.sp) Object.assign(cfg.sp, saved.sp);
    if (saved.ct) Object.assign(cfg.ct, saved.ct);
    if (saved.txt) Object.assign(cfg.txt, saved.txt);
    if (saved.gap) cfg.gap = saved.gap;
  } catch (e) {}

  function save() {
    localStorage.nativeTrainConfig = JSON.stringify(cfg);
  }

  function currentCardObj() {
    try {
      if (typeof currentCard === 'function') {
        const c = currentCard();
        if (c) return c;
      }
      if (typeof allCards !== 'undefined' && typeof index !== 'undefined' && allCards[index]) return allCards[index];
    } catch (e) {}
    return null;
  }

  function currentId() {
    const c = currentCardObj();
    try {
      if (c && c.id) return c.id;
      if (typeof index !== 'undefined') return index + 1;
    } catch (e) {}
    return 1;
  }

  function currentWord() {
    const c = currentCardObj();
    return c ? (c.front || c.word || '') : '';
  }

  function randomCardId() {
    try {
      const cards = filteredCards();
      if (cards && cards.length) return cards[Math.floor(Math.random() * cards.length)].id;
    } catch (e) {}
    return currentId();
  }

  window.nativeJumpToCardId = function (id) {
    try {
      let cards = filteredCards();
      let pos = cards.findIndex(c => Number(c.id) === Number(id));
      if (pos < 0) {
        activeDeck = '全部';
        query = '';
        posFilter = 'all';
        statusFilter = 'all';
        localStorage.setItem('ankiB1HighPlusDeck', '全部');
        localStorage.setItem('ankiB1HighPlusPosFilter', 'all');
        localStorage.setItem('ankiB1HighPlusStatusFilter', 'all');
        resetRandomOrder();
        cards = filteredCards();
        pos = cards.findIndex(c => Number(c.id) === Number(id));
        renderDecks();
      }
      if (pos >= 0) {
        index = pos;
        resetAnswerState();
        flipped = false;
        answerRevealed = false;
        renderCard();
      }
    } catch (e) {}
  };

  function opts() {
    let o = { deLang: 'de', zhLang: 'zh-CN', speed: '0.9' };
    try {
      if (typeof getNativeOptions === 'function') o = getNativeOptions();
    } catch (e) {}
    return o;
  }

  function flash(t) {
    let x = document.getElementById('nativeApkToast');
    if (!x) {
      x = document.createElement('div');
      x.id = 'nativeApkToast';
      x.style.cssText = 'position:fixed;left:50%;top:calc(12px + env(safe-area-inset-top));transform:translateX(-50%);z-index:1000000;max-width:76vw;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;border-radius:999px;padding:7px 11px;background:rgba(18,14,10,.78);color:#fff4df;font-size:12px;font-weight:900;box-shadow:0 8px 20px rgba(0,0,0,.25);opacity:0;transition:.18s;pointer-events:none';
      document.body.appendChild(x);
    }
    x.textContent = t;
    x.style.opacity = '.94';
    clearTimeout(x._tm);
    x._tm = setTimeout(() => { x.style.opacity = '0'; }, 1200);
  }

  const gear = document.createElement('button');
  gear.id = 'nativeGearOnly';
  gear.textContent = '⚙';
  gear.style.cssText = 'position:fixed;right:8px;top:48%;z-index:999999;width:38px;height:38px;border:0;border-radius:999px;background:rgba(255,231,180,.68);color:#251a11;font-size:20px;font-weight:950;box-shadow:0 8px 18px rgba(0,0,0,.26);backdrop-filter:blur(10px);opacity:.72;touch-action:none';
  document.body.appendChild(gear);

  const cover = document.createElement('div');
  cover.id = 'nativeSettingsCover';
  cover.style.cssText = 'position:fixed;inset:0;z-index:1000001;background:rgba(0,0,0,.46);display:none;align-items:flex-end;justify-content:center;overflow:hidden;touch-action:none';

  const sheet = document.createElement('div');
  sheet.style.cssText = 'width:100%;height:86vh;max-height:86vh;min-height:0;overflow-y:scroll;overflow-x:hidden;-webkit-overflow-scrolling:touch;overscroll-behavior:contain;touch-action:pan-y;border-radius:22px 22px 0 0;background:#17110d;color:#fff4df;padding:14px 14px calc(24px + env(safe-area-inset-bottom));box-sizing:border-box;box-shadow:0 -14px 36px rgba(0,0,0,.42);font-family:system-ui,-apple-system,BlinkMacSystemFont,sans-serif';
  cover.appendChild(sheet);
  document.body.appendChild(cover);

  sheet.addEventListener('touchstart', e => e.stopPropagation(), { passive: true });
  sheet.addEventListener('touchmove', e => e.stopPropagation(), { passive: true });
  cover.addEventListener('touchmove', function (e) {
    if (!sheet.contains(e.target)) e.preventDefault();
  }, { passive: false });

  function lockPage() {
    document.body.style.overflow = 'hidden';
    document.documentElement.style.overflow = 'hidden';
  }

  function unlockPage() {
    document.body.style.overflow = '';
    document.documentElement.style.overflow = '';
  }

  function openSheet() {
    render();
    sheet.style.height = Math.floor(window.innerHeight * 0.86) + 'px';
    cover.style.display = 'flex';
    lockPage();
    setTimeout(() => { sheet.scrollTop = 0; }, 0);
  }

  function closeSheet() {
    cover.style.display = 'none';
    unlockPage();
  }

  function btn(t) {
    const b = document.createElement('button');
    b.textContent = t;
    b.style.cssText = 'height:38px;border:0;border-radius:14px;background:#ffe1a3;color:#251a11;font-size:14px;font-weight:950;padding:0 12px';
    return b;
  }

  function smallTitle(t) {
    const x = document.createElement('div');
    x.textContent = t;
    x.style.cssText = 'font-size:15px;font-weight:1000;margin:16px 0 6px;color:#ffe1a3';
    sheet.appendChild(x);
  }

  function row(title, key, type, min, max, step) {
    const r = document.createElement('div');
    r.style.cssText = 'display:grid;grid-template-columns:1fr 42px 52px 42px;gap:7px;align-items:center;margin:8px 0';
    const lab = document.createElement('div');
    lab.textContent = title;
    lab.style.cssText = 'font-size:13px;font-weight:850;color:#fff0d0';
    const m = btn('-');
    const v = document.createElement('div');
    const p = btn('+');
    v.style.cssText = 'text-align:center;font-size:13px;font-weight:950;color:#ffe1a3';
    function draw() { v.textContent = type === 'sp' ? cfg.sp[key].toFixed(2) : cfg.ct[key]; }
    m.onclick = function () {
      if (type === 'sp') cfg.sp[key] = Math.max(min, +(cfg.sp[key] - step).toFixed(2));
      else cfg.ct[key] = Math.max(min, cfg.ct[key] - step);
      save();
      draw();
    };
    p.onclick = function () {
      if (type === 'sp') cfg.sp[key] = Math.min(max, +(cfg.sp[key] + step).toFixed(2));
      else cfg.ct[key] = Math.min(max, cfg.ct[key] + step);
      save();
      draw();
    };
    draw();
    r.append(lab, m, v, p);
    sheet.appendChild(r);
  }

  function render() {
    sheet.innerHTML = '';
    const head = document.createElement('div');
    head.style.cssText = 'display:flex;align-items:center;justify-content:space-between;margin-bottom:10px';
    const title = document.createElement('div');
    title.textContent = '朗读设置';
    title.style.cssText = 'font-size:18px;font-weight:1000';
    const close = btn('关闭');
    close.onclick = closeSheet;
    head.append(title, close);
    sheet.appendChild(head);

    const actions = document.createElement('div');
    actions.style.cssText = 'display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;margin:10px 0 8px';
    const normal = btn('朗读+拼读');
    normal.style.height = '44px';
    normal.onclick = function () {
      const o = opts();
      NativePlayer.startWithOptions(currentId(), 2, 1, cfg.gap, '' + (o.speed || .9), o.deLang, o.zhLang);
      playing = true;
      flash('朗读：' + currentWord());
      closeSheet();
    };
    const train = btn('完整训练');
    train.style.height = '44px';
    train.onclick = function () {
      const o = opts();
      NativePlayer.startTrainingWithConfig(currentId(), cfg.gap, o.deLang, o.zhLang, '' + cfg.sp.wordSlow, '' + cfg.sp.spell, '' + cfg.sp.wordNormal, '' + cfg.sp.phrase, '' + cfg.sp.sentence, '' + cfg.sp.zh, cfg.ct.wordSlow, cfg.ct.spell, cfg.ct.wordNormal, cfg.ct.meaning, cfg.ct.phrase, cfg.ct.sentence, cfg.ct.sentenceCn);
      playing = true;
      flash('训练：' + currentWord());
      closeSheet();
    };
    const random = btn('随机组合');
    random.style.height = '44px';
    random.onclick = function () {
      const o = opts();
      const id = randomCardId();
      window.nativeJumpToCardId(id);
      NativePlayer.startRandomTrainingWithConfig(id, cfg.gap, o.deLang, o.zhLang, '' + cfg.sp.wordSlow, '' + cfg.sp.spell, '' + cfg.sp.wordNormal, '' + cfg.sp.phrase, '' + cfg.sp.sentence, '' + cfg.sp.zh, cfg.ct.wordSlow, cfg.ct.spell, cfg.ct.wordNormal, cfg.ct.meaning, cfg.ct.phrase, cfg.ct.sentence, cfg.ct.sentenceCn);
      playing = true;
      flash('随机组合');
      closeSheet();
    };
    actions.append(normal, train, random);
    sheet.appendChild(actions);

    const stop = btn('停止');
    stop.style.cssText += ';width:100%;height:40px;margin-bottom:10px';
    stop.onclick = function () {
      NativePlayer.stop();
      playing = false;
      flash('已停止');
      closeSheet();
    };
    sheet.appendChild(stop);

    smallTitle('输入文本朗读');
    const ta = document.createElement('textarea');
    ta.value = cfg.txt.text || '';
    ta.placeholder = '在这里输入德语、中文或英文，点下面按钮朗读';
    ta.style.cssText = 'box-sizing:border-box;width:100%;height:96px;border:1px solid rgba(255,225,163,.25);border-radius:16px;background:#241a13;color:#fff4df;font-size:14px;line-height:1.45;padding:10px;outline:none;resize:vertical';
    ta.oninput = function () {
      cfg.txt.text = ta.value;
      save();
    };
    sheet.appendChild(ta);

    const textActions = document.createElement('div');
    textActions.style.cssText = 'display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;margin:8px 0 12px';
    const readDe = btn('读德语');
    readDe.onclick = function () { readText(ta, 'de', cfg.sp.wordNormal, '文本朗读'); };
    const readZh = btn('读中文');
    readZh.onclick = function () { readText(ta, 'zh-CN', cfg.sp.zh, '中文朗读'); };
    const readEn = btn('读英文');
    readEn.onclick = function () { readText(ta, 'en', cfg.sp.wordNormal, '英文朗读'); };
    textActions.append(readDe, readZh, readEn);
    sheet.appendChild(textActions);

    smallTitle('速度');
    row('单词慢速', 'wordSlow', 'sp', .4, 1.3, .05);
    row('拼读速度', 'spell', 'sp', .4, 1.3, .05);
    row('单词正常', 'wordNormal', 'sp', .45, 1.6, .05);
    row('词块速度', 'phrase', 'sp', .4, 1.3, .05);
    row('句子速度', 'sentence', 'sp', .4, 1.3, .05);
    row('中文速度', 'zh', 'sp', .45, 1.5, .05);

    smallTitle('次数');
    row('单词慢速次数', 'wordSlow', 'ct', 0, 10, 1);
    row('拼读次数', 'spell', 'ct', 0, 10, 1);
    row('单词正常次数', 'wordNormal', 'ct', 0, 10, 1);
    row('中文意思次数', 'meaning', 'ct', 0, 10, 1);
    row('词块整体次数', 'phrase', 'ct', 0, 10, 1);
    row('句子整体次数', 'sentence', 'ct', 0, 10, 1);
    row('句子中文次数', 'sentenceCn', 'ct', 0, 10, 1);

    const bottom = document.createElement('div');
    bottom.style.cssText = 'height:40px';
    sheet.appendChild(bottom);
  }

  function readText(ta, lang, spd, label) {
    const t = ta.value.trim();
    if (!t) {
      flash('先输入文本');
      return;
    }
    cfg.txt.text = t;
    cfg.txt.lang = lang;
    save();
    NativePlayer.speakText(t, lang, '' + spd, Math.max(1, cfg.txt.repeat || 1), cfg.gap);
    playing = true;
    flash(label);
    closeSheet();
  }

  gear.onclick = function () {
    if (moved) {
      moved = false;
      return;
    }
    openSheet();
  };

  gear.ontouchstart = function (e) {
    const t = e.touches[0];
    const r = gear.getBoundingClientRect();
    sx = t.clientX;
    sy = t.clientY;
    ox = r.left;
    oy = r.top;
    moved = false;
  };

  gear.ontouchmove = function (e) {
    const t = e.touches[0];
    const dx = t.clientX - sx;
    const dy = t.clientY - sy;
    if (Math.abs(dx) + Math.abs(dy) > 5) moved = true;
    gear.style.left = Math.max(4, Math.min(innerWidth - 42, ox + dx)) + 'px';
    gear.style.top = Math.max(40, Math.min(innerHeight - 60, oy + dy)) + 'px';
    gear.style.right = 'auto';
  };
})();
