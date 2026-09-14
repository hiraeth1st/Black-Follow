/* Runs only in the authenticated Instagram top-level page. Observes requests
 * made by the page; never sends its own API request or exports cookies. */
(function (config) {
  'use strict';
  if (location.origin !== 'https://www.instagram.com' || window.top !== window) return false;
  if (window.__bfScrollScan) return window.__bfScrollScan.token === config.token;
  const state = {queue: [], pending: 0, pages: 0, terminal: false, error: '', lastData: Date.now(), lastMove: 0, clicked: false};
  const targetPath = '/api/v1/friendships/' + config.id + '/' + config.kind + '/';
  function requestInfo(raw) {
    try {
      const u = new URL(typeof raw === 'string' ? raw : raw.url, location.href);
      if (u.origin !== location.origin || u.pathname !== targetPath) return null;
      // Search results cannot prove that the unfiltered list is complete.
      if ((u.searchParams.get('query') || '').trim()) {state.error = 'BF_WEB_SEARCH'; return null;}
      return {cursor: u.searchParams.get('max_id') || ''};
    } catch (_) { return null; }
  }
  function push(info, status, body, retry) {
    if (!info) return;
    if (state.queue.length >= 12) {state.error = 'BF_WEB_QUEUE'; return;}
    const message = body && typeof body.message === 'string' ? body.message : '';
    const gate = status === 429 || /^please wait a few minutes/i.test(message) || message === 'please_wait_a_few_minutes' ? 'rate' :
      status === 401 || message === 'login_required' ? 'auth' :
      status === 403 || body && (body.challenge || body.checkpoint_url || body.feedback_title) || /^(challenge_required|checkpoint_required|feedback_required)$/.test(message) ? 'restricted' : '';
    if (gate || status !== 200) {
      state.queue.push({gate: gate || 'http', status: status, retry: retry || ''});state.error = 'BF_WEB_ACCESS';return;
    }
    if (!body || body.status && body.status !== 'ok' || !Array.isArray(body.users) || body.users.length > 1000) {state.error = 'BF_WEB_SCHEMA';return;}
    const users = [];
    for (const u of body.users) {
      const rawId = u.pk == null ? u.id : u.pk;
      // JS numbers beyond its exact integer range must never become an identity.
      if (typeof rawId === 'number' && !Number.isSafeInteger(rawId)) {state.error = 'BF_WEB_ID';return;}
      const id = String(rawId == null ? '' : rawId), username = u.username;
      if (!/^\d+$/.test(id) || typeof username !== 'string' || !/^[A-Za-z0-9._]{1,30}$/.test(username)) {state.error = 'BF_WEB_ID';return;}
      users.push({id: id, username: username, name: typeof u.full_name === 'string' ? u.full_name.slice(0,200) : '', avatar: typeof u.profile_pic_url === 'string' ? u.profile_pic_url.slice(0,2048) : ''});
    }
    const next = body.next_max_id == null ? '' : String(body.next_max_id);
    if (next.length > 4096) {state.error = 'BF_WEB_SCHEMA';return;}
    const more = body.has_more === true || next !== '';
    state.queue.push({cursor: info.cursor, next: next, more: more, users: users});
    state.pages++;state.terminal = !more;state.lastData = Date.now();
  }
  const originalFetch = window.fetch;
  window.fetch = function () {
    const info = requestInfo(arguments[0]);if (info) state.pending++;
    let result;
    try {result = originalFetch.apply(this, arguments);} catch (e) {if(info){state.pending--;state.error='BF_WEB_NETWORK';}throw e;}
    return result.then(response => {
      if(info) response.clone().json().then(body => push(info,response.status,body,response.headers.get('Retry-After')),
        () => push(info,response.status,null,response.headers.get('Retry-After'))).then(() => {state.pending--;}, () => {state.pending--;state.error='BF_WEB_SCHEMA';});
      return response;
    }, error => {if(info){state.pending--;state.error='BF_WEB_NETWORK';}throw error;});
  };
  const originalOpen = XMLHttpRequest.prototype.open, originalSend = XMLHttpRequest.prototype.send;
  const metadata = new WeakMap();
  XMLHttpRequest.prototype.open = function (method,url) {metadata.set(this,requestInfo(url));return originalOpen.apply(this,arguments);};
  XMLHttpRequest.prototype.send = function () {
    const info=metadata.get(this);
    if(info) {
      state.pending++;
      this.addEventListener('loadend',function () {
        try {let body=null;try {body=this.responseType==='json'?this.response:JSON.parse(this.responseText);}catch(_){}
          push(info,this.status,body,this.getResponseHeader('Retry-After'));
        } finally {state.pending--;}
      },{once:true});
    }
    try {return originalSend.apply(this,arguments);}catch(e){if(info){state.pending--;state.error='BF_WEB_NETWORK';}throw e;}
  };
  function visible(e) {const r=e.getBoundingClientRect();return r.width>0 && r.height>0;}
  function listRoot() {
    const dialogs=Array.from(document.querySelectorAll('[role="dialog"]')).filter(visible);
    if(dialogs.length)return dialogs[dialogs.length-1];
    if(location.pathname.replace(/\/$/,'').toLowerCase() === ('/'+config.username+'/'+config.kind).toLowerCase())return document.body;
    return null;
  }
  function container(root) {
    const links=Array.from(root.querySelectorAll('a[href]')).filter(a => {
      try {const u=new URL(a.href,location.href);return u.origin===location.origin && /^\/[A-Za-z0-9._]{1,30}\/$/.test(u.pathname) && visible(a);}catch(_){return false;}
    });
    for(const link of links) for(let e=link.parentElement;e && root.contains(e);e=e.parentElement) {
      if(e.scrollHeight>e.clientHeight+2 && e.clientHeight>0 && /auto|scroll/.test(getComputedStyle(e).overflowY))return e;
      if(e===root)break;
    }
    if(root===document.body)return document.scrollingElement;
    if(root.scrollHeight>root.clientHeight+2 && /auto|scroll/.test(getComputedStyle(root).overflowY))return root;
    return null;
  }
  function step(move) {
    const result={token:config.token,error:state.error,pages:state.pages,pending:state.pending,packets:state.queue.splice(0),moved:0,ended:false};
    if(state.error)return result;
    if(!state.clicked && move) {
      const wanted=('/'+config.username+'/'+config.kind+'/').toLowerCase();
      const link=Array.from(document.querySelectorAll('a[href]')).find(a => {
        try {const u=new URL(a.href,location.href);return u.origin===location.origin && (u.pathname.replace(/\/$/,'')+'/').toLowerCase()===wanted && visible(a);}catch(_){return false;}
      });
      if(link){state.clicked=true;link.click();state.lastMove=Date.now();return result;}
    }
    const root=listRoot();
    if(!root || !state.pages)return result;
    const scroller=container(root);
    const spinner=Array.from(root.querySelectorAll('[role="progressbar"],[aria-busy="true"]')).some(visible);
    if(state.pending || spinner || Date.now()-state.lastData<1500 || Date.now()-state.lastMove<1500)return result;
    const atBottom=scroller ? scroller.scrollTop+scroller.clientHeight>=scroller.scrollHeight-3 : true;
    if(atBottom && state.terminal){result.ended=true;return result;}
    if(move && scroller && !atBottom) {
      const distance=Math.max(1,Math.floor(Math.min(scroller.clientHeight,innerHeight)*0.5));
      scroller.scrollBy({top:distance,left:0,behavior:'smooth'});state.lastMove=Date.now();result.moved=distance;
    }
    return result;
  }
  window.__bfScrollScan={token:config.token,step:step};
  return true;
})(__BF_CONFIG__);
