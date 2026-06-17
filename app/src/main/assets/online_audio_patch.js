(function(){
  if(window.__LEXORA_ONLINE_AUDIO_PATCH__) return;
  window.__LEXORA_ONLINE_AUDIO_PATCH__ = true;

  var RealAudio = window.Audio;
  var map = {};

  function enc(s){
    return encodeURIComponent(String(s || ''));
  }

  function cleanWord(s){
    return String(s || '')
      .replace(/\(.*?\)/g, '')
      .replace(/^(der|die|das|ein|eine|einen|einem|einer)\s+/i, '')
      .trim();
  }

  function spell(s){
    var t = cleanWord(s).replace(/[^A-Za-zÄÖÜäöüß0-9]/g, '');
    var out = [];
    for(var i = 0; i < t.length; i++){
      out.push(t[i].toLowerCase() === 'ß' ? 'Eszett' : t[i].toLowerCase());
    }
    return out.join(', ');
  }

  function tts(text, lang){
    return 'https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=' + enc(lang) + '&q=' + enc(text);
  }

  function loadCards(){
    try{
      var x = new XMLHttpRequest();
      x.open('GET', 'cards.tsv', true);
      x.onreadystatechange = function(){
        if(x.readyState !== 4) return;
        if(x.status !== 200 && x.status !== 0) return;
        var lines = String(x.responseText || '').replace(/\r/g, '').split('\n');
        for(var i = 0; i < lines.length; i++){
          if(!lines[i]) continue;
          var p = lines[i].split('\t');
          map[p[0]] = {
            word: p[4] || '',
            cn: p[6] || '',
            ex: p[7] || '',
            excn: p[8] || ''
          };
        }
      };
      x.send();
    }catch(e){}
  }

  function patchedAudio(url){
    var u = String(url || '');
    var m = u.match(/^audio\/([^_]+)_(word|spell|cn|ex|excn)\.mp3$/);

    if(m){
      var id = m[1];
      var type = m[2];
      var r = map[id] || {};
      var text = '';
      var lang = 'de';

      // 单词发音不要拦截，让 index.html 先找本地真人音频，失败后再找 Wikimedia/词典真人音频。
      if(type === 'word') return new RealAudio(url);

      if(type === 'spell'){
        text = spell(r.word);
        lang = 'de';
      }else if(type === 'cn'){
        text = r.cn;
        lang = 'zh-CN';
      }else if(type === 'ex'){
        text = r.ex;
        lang = 'de';
      }else if(type === 'excn'){
        text = r.excn;
        lang = 'zh-CN';
      }

      if(text) return new RealAudio(tts(text, lang));
    }

    return new RealAudio(url);
  }

  patchedAudio.prototype = RealAudio.prototype;
  window.Audio = patchedAudio;
  loadCards();
})();
