/*
 * Chimahon Huan: range-based headword scanning and matched-range geometry.
 * Follows Yomitan's scan text -> match -> source Range rectangles -> popup flow.
 * This is an Android WebView adapter, not the Yomitan extension runtime.
 * GPL-3.0-or-later. No network, capture, storage or analytics operations.
 */
(function () {
  'use strict';
  const renderer = window.DictionaryRenderer;
  if (!renderer || window.ChimaHeadwordSelection) return;
  const SKIP = 'rt, rp, button, a, summary, .tag, .headword-tag-list, .entry-icon-group';
  const LIMIT = 24;
  let enabled = false;
  let gesture = null;
  let active = null;
  let markers = [];
  const originalResolve = renderer.resolveRecursiveSelection.bind(renderer);
  const originalClear = renderer.clearRecursiveSelection.bind(renderer);
  const originalEnable = renderer.setRecursiveLookupEnabled.bind(renderer);

  function rootAt(target) {
    const el = target?.nodeType === Node.TEXT_NODE ? target.parentElement : target;
    return el?.closest && !el.closest(SKIP) ? el.closest('.headword') : null;
  }
  function glyphs(root) {
    const result = [];
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: (node) => node.parentElement?.closest(SKIP)
        ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT,
    });
    let node;
    while ((node = walker.nextNode())) {
      let offset = 0;
      for (const character of node.textContent || '') {
        const range = document.createRange();
        range.setStart(node, offset);
        range.setEnd(node, offset + character.length);
        result.push({character, range});
        offset += character.length;
      }
    }
    return result;
  }
  function rectsOf(items) {
    return items.flatMap(({range}) => Array.from(range.getClientRects()))
      .filter((r) => r.width > 0 && r.height > 0)
      .map((r) => ({left:r.left, top:r.top, right:r.right, bottom:r.bottom}));
  }
  function contains(rects, x, y) {
    return rects.some((r) => x >= r.left && x <= r.right && y >= r.top && y <= r.bottom);
  }
  function selectionAt(root, x, y) {
    const selection = window.getSelection();
    if (!selection || selection.isCollapsed || !selection.rangeCount) return null;
    const range = selection.getRangeAt(0);
    if (!root.contains(range.startContainer) || !root.contains(range.endContainer)) return null;
    const items = glyphs(root);
    const indexes = [];
    items.forEach((item, index) => {
      const r = item.range;
      if (range.comparePoint(r.startContainer, r.startOffset) === 0 &&
          range.comparePoint(r.endContainer, r.endOffset) === 0) indexes.push(index);
    });
    if (!indexes.length) return null;
    const start = indexes[0], end = indexes[indexes.length - 1] + 1;
    const chosen = items.slice(start, end);
    if (!contains(rectsOf(chosen), x, y)) return null;
    const query = chosen.map((g) => g.character).join('').trim();
    if (!query) return null;
    return {root, items, start, end, query, exact:true,
      sentence:items.map((g) => g.character).join(''),
      offset:items.slice(0,start).map((g) => g.character).join('').length};
  }
  function atPoint(root, x, y) {
    const items = glyphs(root);
    const start = items.findIndex((g) => contains(rectsOf([g]), x, y));
    if (start < 0) return null;
    let end = start;
    while (end < items.length && end - start < LIMIT &&
        !/[\s。、！？…「」『』（）()【】〈〉《》〔〕{}\[\]・：；:;，,.]/u.test(items[end].character)) end++;
    if (end === start) return null;
    return {root, items, start, end, query:items.slice(start,end).map((g)=>g.character).join(''),
      sentence:items.map((g)=>g.character).join(''), exact:false,
      offset:items.slice(0,start).map((g)=>g.character).join('').length};
  }
  function clearOwn() {
    markers.forEach((m) => m.remove());
    markers = [];
    active = null;
  }
  function geometry(count) {
    if (!active || !active.root.isConnected || !Number.isFinite(count) || count <= 0) return null;
    const end = Math.min(active.end, active.start + Math.floor(count));
    const rects = rectsOf(active.items.slice(active.start,end));
    if (!rects.length) return null;
    const left = Math.min(...rects.map((r)=>r.left));
    const top = Math.min(...rects.map((r)=>r.top));
    const right = Math.max(...rects.map((r)=>r.right));
    const bottom = Math.max(...rects.map((r)=>r.bottom));
    return {x:left,y:top,width:right-left,height:bottom-top,rects,viewportWidth:window.innerWidth};
  }
  renderer.resolveRecursiveSelection = function (count) {
    if (!active) return originalResolve(count);
    markers.forEach((m)=>m.remove()); markers=[];
    const result = geometry(count);
    if (!result) return null;
    result.rects.forEach((r) => {
      const marker=document.createElement('span');
      marker.className='recursive-lookup-highlight';
      marker.style.cssText=`position:absolute;pointer-events:none;left:${r.left+window.scrollX}px;top:${r.bottom+window.scrollY-2}px;width:${r.right-r.left}px;height:2px`;
      document.body.appendChild(marker); markers.push(marker);
    });
    return JSON.stringify(result);
  };
  renderer.clearRecursiveSelection = function () { clearOwn(); originalClear(); };
  renderer.setRecursiveLookupEnabled = function (value) {
    enabled=!!value;
    if (!enabled) { gesture=null; clearOwn(); }
    return originalEnable(value);
  };
  document.addEventListener('pointerdown', (e) => {
    const root=rootAt(e.target);
    if (!enabled || !root) { gesture=null; return; }
    if (e.isPrimary === false) { gesture=null; return; }
    // Snapshot the actual Range before WebView collapses its native selection.
    gesture={root,x:e.clientX,y:e.clientY,id:e.pointerId,time:performance.now(),moved:false,
      selection:selectionAt(root,e.clientX,e.clientY)};
  }, {capture:true,passive:true});
  document.addEventListener('pointermove', (e) => {
    if (gesture && gesture.id===e.pointerId &&
      Math.hypot(e.clientX-gesture.x,e.clientY-gesture.y)>10) gesture.moved=true;
  }, {capture:true,passive:true});
  document.addEventListener('pointercancel', () => { gesture=null; }, {capture:true,passive:true});
  document.addEventListener('scroll', () => { gesture=null; }, {capture:true,passive:true});
  document.addEventListener('click', (e) => {
    const root=rootAt(e.target);
    if (!root) { active=null; return; }
    if (!enabled) { e.stopImmediatePropagation(); return; }
    const down=gesture; gesture=null;
    if (down && (down.moved || performance.now()-down.time>450)) {
      // A drag/long press is not a lookup click. Preserve native selection.
      e.stopImmediatePropagation(); return;
    }
    let request=selectionAt(root,e.clientX,e.clientY);
    if (!request && down?.root===root && down.selection &&
      Math.hypot(e.clientX-down.x,e.clientY-down.y)<=10) request=down.selection;
    request=request || atPoint(root,e.clientX,e.clientY);
    if (!request) { e.stopImmediatePropagation(); return; }
    clearOwn(); originalClear(); active=request;
    const rect=geometry(request.exact ? request.end-request.start : 1);
    const x=rect?.x ?? e.clientX, y=rect?.y ?? e.clientY;
    const params=new URLSearchParams({q:request.query,sentence:request.sentence,
      offset:String(request.offset),x:String(Math.round(x)),y:String(Math.round(y))});
    // Same native dictionary path as existing recursion, never a web request.
    e.preventDefault(); e.stopImmediatePropagation();
    window.location.href='chima://lookup?'+params.toString();
  }, {capture:true,passive:false});
  // Exposed geometry inspection enables browser regression tests without OCR,
  // translation, dictionary servers or changes to Android permission handling.
  window.ChimaHeadwordSelection={
    scanAt(root,x,y) { const r=selectionAt(root,x,y)||atPoint(root,x,y);
      return r ? {query:r.query,sentence:r.sentence,offset:r.offset,exact:r.exact} : null; },
    matchedGeometry:geometry,
  };
})();
