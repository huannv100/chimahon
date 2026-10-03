"""Run real Chromium DOM/pointer tests; URI navigation is captured as a test sink.
Does not claim to test Android Accessibility, Google translation or the native DB.
"""
from pathlib import Path
import json, os, sys
from playwright.sync_api import sync_playwright
assets = Path(sys.argv[1] if len(sys.argv)>1 else 'chimahon/src/main/assets/dictionary')
checks=[]
def ok(name, condition):
    assert condition, name
    checks.append(name)
with sync_playwright() as pw:
    options={'headless':True,'args':['--no-sandbox']}
    if os.path.exists('/usr/bin/chromium'): options['executable_path']='/usr/bin/chromium'
    browser=pw.chromium.launch(**options)
    page=browser.new_page(viewport={'width':412,'height':850},has_touch=True,device_scale_factor=2)
    page.set_content('<meta name="viewport" content="width=device-width,initial-scale=1"><style id="dictionary-styles"></style><main id="entries"></main>')
    page.add_style_tag(content=(assets/'base.css').read_text())
    core=(assets/'renderer.js').read_text().replace('window.location.href = url;', 'window.__lookups.push(url);').replace("window.location.href='chima://lookup?'+params.toString();", "window.__lookups.push('chima://lookup?'+params.toString());")
    addon=(assets/'headword-selection.js').read_text().replace("window.location.href='chima://lookup?'+params.toString();", "window.__lookups.push('chima://lookup?'+params.toString());")
    page.evaluate('window.__lookups=[]')
    page.add_script_tag(content=core)
    page.add_script_tag(content=addon)
    page.evaluate('''() => {
      DictionaryRenderer.setRecursiveLookupEnabled(true);
      DictionaryRenderer.render({results:[{matched:'中华人民共和国',term:{expression:'中华人民共和国',reading:'zhōng huá rén mín gòng hé guó',glossaries:[]}}],tabs:[],wordAudioAutoplay:false});
    }''')
    # Collect actual glyph ranges from the real generated headword. Exclude ruby annotations.
    page.evaluate('''() => {window.glyphs=()=>{
      const w=document.createTreeWalker(document.querySelector('.headword'),NodeFilter.SHOW_TEXT,{acceptNode:n=>n.parentElement.closest('rt,rp,.tag')?NodeFilter.FILTER_REJECT:NodeFilter.FILTER_ACCEPT});
      const a=[];let n;while(n=w.nextNode()){let i=0;for(const c of n.textContent){const r=document.createRange();r.setStart(n,i);r.setEnd(n,i+c.length);a.push({c,r});i+=c.length;}}return a;};
      window.point=(i)=>{const r=glyphs()[i].r.getBoundingClientRect();return {x:r.left+r.width*.35,y:r.top+r.height*.6};};
      window.choose=(a,b)=>{const g=glyphs();const r=document.createRange();r.setStart(g[a].r.startContainer,g[a].r.startOffset);r.setEnd(g[b-1].r.endContainer,g[b-1].r.endOffset);const s=getSelection();s.removeAllRanges();s.addRange(r);};
    }''')
    point=page.evaluate('point(2)')
    page.touchscreen.tap(**point)
    query=page.evaluate("new URL(__lookups.at(-1)).searchParams.get('q')")
    ok('tap inside headword scans the remaining expression',query=='人民共和国')
    geo=page.evaluate('JSON.parse(DictionaryRenderer.resolveRecursiveSelection(2))')
    selected=page.evaluate('glyphs().slice(2,4).map(g=>{const r=g.r.getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom};})')
    ok('matched popup anchor covers exactly two matched characters',abs(geo['x']-selected[0]['left'])<1 and abs(geo['width']-(selected[-1]['right']-selected[0]['left']))<1)
    ok('ruby reading is excluded from matched bounds',abs(geo['y']-selected[0]['top'])<1)
    page.evaluate('choose(2,4)')
    page.touchscreen.tap(**point)
    ok('manual selection queries only 人民',page.evaluate("new URL(__lookups.at(-1)).searchParams.get('q')")=='人民')
    ok('manual selection retains a non-null popup anchor',page.evaluate('DictionaryRenderer.resolveRecursiveSelection(2)!==null'))
    # Deliberately emulate the WebView selection disappearing after pointerdown.
    page.evaluate('''({x,y})=>{choose(2,4);const t=document.elementFromPoint(x,y);t.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,clientX:x,clientY:y,pointerId:77,isPrimary:true}));getSelection().removeAllRanges();t.dispatchEvent(new MouseEvent('click',{bubbles:true,clientX:x,clientY:y}));}''',point)
    ok('selection survives collapse between pointerdown and click',page.evaluate("new URL(__lookups.at(-1)).searchParams.get('q')")=='人民')
    page.evaluate('getSelection().removeAllRanges()')
    before=page.evaluate('__lookups.length')
    page.evaluate('''({x,y})=>{const t=document.elementFromPoint(x,y);t.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,clientX:x,clientY:y,pointerId:1,isPrimary:true}));t.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,clientX:x+40,clientY:y,pointerId:1}));t.dispatchEvent(new MouseEvent('click',{bubbles:true,clientX:x,clientY:y}));}''',point)
    ok('drag does not trigger a lookup',page.evaluate('__lookups.length')==before)
    # Control clicks must not be repurposed as headword scanning.
    page.evaluate("document.querySelector('.headword').insertAdjacentHTML('beforeend','<button id=control>人民</button>')")
    page.locator('#control').click()
    ok('buttons inside the headword remain controls',page.evaluate('__lookups.length')==before)
    page.evaluate("document.querySelector('#control').remove();DictionaryRenderer.clearRecursiveSelection()")
    ok('clearing selection clears matched bounds',page.evaluate('ChimaHeadwordSelection.matchedGeometry(2)===null'))
    page.evaluate('DictionaryRenderer.setRecursiveLookupEnabled(false)')
    before=page.evaluate('__lookups.length')
    page.touchscreen.tap(**point)
    ok('disabled recursion does not dispatch a lookup',page.evaluate('__lookups.length')==before)
    ok('adapter returns no stale matched range while disabled',page.evaluate('ChimaHeadwordSelection.matchedGeometry(2)===null'))
    page.evaluate('''() => {DictionaryRenderer.setRecursiveLookupEnabled(true);DictionaryRenderer.render({results:[{matched:'𠮷人民',term:{expression:'𠮷人民',reading:'',glossaries:[]}}],tabs:[]});}''')
    point=page.evaluate('point(1)')
    page.touchscreen.tap(**point)
    ok('supplementary Han code point does not corrupt following offset',page.evaluate("new URL(__lookups.at(-1)).searchParams.get('offset')")=='2')
    ok('supplementary character does not force kanji-only lookup',page.evaluate("new URL(__lookups.at(-1)).host")=='lookup')
    browser.close()
print(json.dumps({'status':'passed','checks':checks},ensure_ascii=False,indent=2))
