(function () {
    // Read rendered navigation only. Never read passwords, cookies, scripts or app internals.
    if (location.protocol !== 'https:' || !/^(www\.)?instagram\.com$/.test(location.hostname)) return '';
    if (/^\/(accounts|challenge|checkpoint|oauth)(\/|$)/.test(location.pathname)) return '';
    var visible = function (e) {
        return !!e && e.getClientRects().length > 0 && getComputedStyle(e).visibility !== 'hidden';
    };
    if (Array.from(document.querySelectorAll('input[type="password"]')).some(visible)) return '';
    var found = [];
    Array.from(document.querySelectorAll('a[href]')).forEach(function (a) {
        if (!visible(a) || a.closest('main,[role="main"],[role="dialog"],article')) return;
        if (a.origin !== location.origin) return;
        var match = /^\/([A-Za-z0-9._]{1,30})\/$/.exec(a.pathname);
        if (!match || /^(explore|reels|direct|accounts|stories|about|legal|developer|p|tv)$/.test(match[1])) return;
        var img = a.querySelector('img');
        var label = (a.getAttribute('aria-label') || a.innerText || '').trim().toLowerCase();
        var alt = img ? (img.getAttribute('alt') || '').toLowerCase() : '';
        if (label !== 'profile' && label !== 'profil' && !(visible(img) && alt.indexOf(match[1].toLowerCase()) === 0)) return;
        for (var parent = a.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
            // A feed, suggestion panel or target profile must never identify the signed-in viewer.
            if (parent.matches('main,[role="main"],[role="dialog"],article') || parent.querySelector('main,[role="main"],article')) break;
            var paths = Array.from(parent.querySelectorAll('a[href]')).filter(visible).map(function (link) { return link.pathname; });
            if (paths.length <= 20 && paths.indexOf('/') >= 0 && paths.indexOf('/reels/') >= 0 && paths.indexOf('/explore/') >= 0) {
                if (found.indexOf(match[1]) < 0) found.push(match[1]);
                break;
            }
        }
    });
    return found.length === 1 ? found[0] : '';
})()
