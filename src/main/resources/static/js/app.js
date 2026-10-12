/* ============================================================
   UnderFaker 프론트엔드 — index.html 인라인 스크립트를 분리한 파일
   위치: projectbase/src/main/resources/static/js/app.js
   index.html 은 <script src="js/app.js"> 한 줄로 불러온다.

   ※ 경로가 /js/ 아래여야 하는 이유 (9/24 2차)
     SecurityConfig 가 정적 리소스로 공개하는 경로는 /, /index.html, /favicon.ico,
     /css/**, /js/**, /img/**, /assets/** 뿐이다. 처음에 static/app.js 로 두었더니
     /app.js 요청이 JWT 필터에 걸려 401 이 났다 — <script> 요청에는 Authorization 헤더가
     안 붙으므로 로그인해도 마찬가지다. 스크립트가 통째로 안 올라와서 화면이 죽는다.
     SecurityConfig 를 고치지 않고 이미 열린 /js/** 로 옮겼다.

   고친 것
   ──────────────────────────────────────────────────────────
   P0-1 응답 필드명 불일치
        기존 render() 는 d.recallType / d.recallMethod / d.recallBusiness /
        d.recallDate / d.recallDefect / d.recallRisk / d.recallBarcode /
        d.productName / d.brandName / d.modelName / d.barcodeNum 을 읽었다.
        VerificationResultResponse 에 실제로 있는 필드는
        verificationId, finalResult, similarityScore, recallUid,
        recallProductName, recallBrandName, recallModelName,
        recallTypeName, recallMeans, recallCmpnyName, makerName,
        harmDscr, accidentCaseDscr, publishActionDscr, publishDate, createdAt
        뿐이다. 16개 중 5개만 맞았고 나머지는 전부 undefined → '-' 로 찍혔다.
        DEMO_MODE 더미 객체가 옛 필드명으로 만들어져 있어서 더미로 볼 때는
        정상으로 보였다. 그래서 이 버그가 안 잡혔다.
        → 매핑을 DTO 기준으로 전부 교정. 더미 객체도 백엔드와 같은 필드명으로 바꿔서
          앞으로 같은 종류의 불일치가 더미에서 그대로 재현되게 했다.

   P0-2 쿠팡 결과 수신 경로가 죽어 있음
        window.addEventListener('message', ...) 로 COUPANG_RECALL_RESULT 를
        기다렸지만 크롬 확장(content.js)은 postMessage 를 하지 않는다.
        chrome.runtime.sendMessage → background.js →
        POST /api/verifications/manual/batch 로 백엔드에 직접 저장한다.
        → 리스너와 localStorage 이력 저장을 삭제하고
          GET /api/verifications/me?channel=EXTENSION 으로 읽는다.
          (channel 파라미터는 9/13 부터 백엔드에 있다.)

   P1   이력 탭을 channel 로 분리. v.source(프론트가 직접 붙였던 값) 제거.
        demo-token 을 Authorization 헤더로 보내지 않게 차단.
        #historyMenuWrap, #coupangLoginRequired 없을 때 터지던 부분 방어.
        updateLoginMessages() 의 동일한 if/else 정리.
        화면 이동 시 결과 패널 .show 해제.

   P2   판정 근거 카드에 항목별 대조 표(MatchEvidenceResponse.comparisons) 렌더.
        이력 항목 클릭 → 그 건의 결과/근거 다시 열기.

   9/24 2차 보완
   ──────────────────────────────────────────────────────────
   - 크롬 확장과 JWT 동기화. 확장(auth-bridge)은 /api/auth/login 응답만 가로채서
     토큰을 얻는다. 어제 로그인해 두고 오늘 페이지만 연 경우엔 확장이 토큰을 모른다.
     → 페이지가 뜰 때 저장된 토큰을 같은 이벤트(underfaker-jwt-captured)로 알려 준다.
     → 로그아웃·401 때는 underfaker-jwt-cleared 로 확장 쪽 토큰도 버리게 한다.
   - 쿠팡 화면: 확장 설치 여부 표시, 최근 EXTENSION 검증 목록, 화면을 보는 동안 자동 새로고침.
     쿠팡 쪽 페이지에 버튼·결과가 뜬다는 기존 안내문은 실제 동작과 달라 고쳤다.
   - '자주 확인하는 제품' 칩 클릭 → 제품명 칸 채우기. 입력칸에서 Enter → 검증/로그인.

   백엔드와 대조해 확인한 것 (9/24)
   ──────────────────────────────────────────────────────────
   - VerificationStatus = PENDING / DONE / FAILED. DONE 이 정상이다.
     (처음에 COMPLETED 로 가정했다가 enum 을 읽고 고쳤다.)
   - ApiResponse = {success, data, code, message}. 프론트가 읽는 모양과 같다.
   - FIELD_LABEL 은 MatchingService.korean() 과 문자열까지 일치시켰다.
============================================================ */

/* ==================================================
   API / AUTH
================================================== */

const API = '';

const DEMO_MODE =
    location.protocol === 'file:' ||
    new URLSearchParams(location.search).get('demo') === '1';

const DEMO_TOKEN = 'demo-token';

let token = null;
let userEmail = null;

try {
  token = localStorage.getItem('rc_token');
  userEmail = localStorage.getItem('rc_email');
} catch (e) {
  /* 시크릿 창 등에서 localStorage 가 막힐 수 있다 */
}

if (DEMO_MODE) {
  token = token || DEMO_TOKEN;
  userEmail = userEmail || 'demo@example.com';
}

/* JWT payload 의 exp(초)를 읽어 만료 여부를 본다. 서명 검증이 아니라 화면 상태용이다.
   만료된 토큰을 들고 '로그인됨'으로 그리면 모든 호출이 401 이 나고, 확장에도 죽은 토큰을
   넘기게 된다. 해석이 안 되는 토큰은 만료로 보지 않는다 — 판단은 서버가 한다. */
function tokenExpired(t) {
  if (!t || t === DEMO_TOKEN) return false;
  try {
    const part = t.split('.')[1];
    if (!part) return false;
    const json = atob(part.replace(/-/g, '+').replace(/_/g, '/'));
    const exp = JSON.parse(json).exp;
    return typeof exp === 'number' && exp * 1000 <= Date.now();
  } catch (e) {
    return false;
  }
}

if (!DEMO_MODE && tokenExpired(token)) {
  token = null;
  userEmail = null;
  try {
    localStorage.removeItem('rc_token');
    localStorage.removeItem('rc_email');
  } catch (e) { /* noop */ }
}

/* 화면에 뿌릴 "내가 입력한 제품".
   VerificationResultResponse 에는 입력값이 안 내려온다(리콜 공표문 쪽만 내려온다).
   그래서 입력 칸은 서버 응답이 아니라 이 변수에서 채운다.
   이력에서 과거 건을 열 때는 /evidence 의 comparisons[].inputValue 로 채운다. */
let lastInput = emptyInput();

/* DEMO_MODE 이력. localStorage 를 쓰지 않는다(P0-2). 새로고침하면 사라진다. */
const DEMO_HISTORY = { WEB: [], EXTENSION: [] };
const DEMO_EVIDENCE = {};

function emptyInput() {
  return {
    productName: null,
    brandName: null,
    modelName: null,
    makerName: null,
    barcodeNum: null,
    certNum: null,
    thumbnailUrl: null
  };
}

/* 9/27 — 상품 이미지 주소(선택). ManualInputRequest.thumbnailUrl 은 @Size(max = 500).
   서버(Google Vision)가 직접 내려받아야 하므로 http(s) 주소만 받는다.
   '이미지 복사'로 붙여 넣은 data: 주소나 파일 경로는 서버가 읽을 수 없다.
   문제가 없으면 null, 있으면 사용자에게 보여 줄 문구를 돌려준다. */
const THUMBNAIL_URL_MAX = 500;

function thumbnailUrlProblem(url) {
  if (!url) return null;
  if (!/^https?:\/\/[^\s]+$/i.test(url)) {
    return '상품 이미지 주소는 http:// 또는 https:// 로 시작하는 주소여야 합니다. (사진에서 오른쪽 버튼 → 이미지 주소 복사)';
  }
  if (url.length > THUMBNAIL_URL_MAX) {
    return '상품 이미지 주소가 너무 깁니다 (' + url.length + '자, 최대 ' + THUMBNAIL_URL_MAX + '자).';
  }
  return null;
}


/* ==================================================
   INITIAL
================================================== */

document.addEventListener('DOMContentLoaded', function () {
  paintHeader();
  bindEnterKeys();
  bindCategoryChips();
  showScreen('verifyScreen');
  if (token) announceTokenToExtension();
  else if (!DEMO_MODE) announceLogoutToExtension();
  /* 10/12 — 이 화면이 없을 때 쇼핑몰에서 가져온 값(확장 0.6.0)이 있으면 받는다 */
  pullPendingCaptures();
});


/* ==================================================
   크롬 확장 연동
   확장의 auth-bridge-isolated.js 가 window 이벤트를 듣고 background 로 넘긴다.
   페이지와 확장은 DOM 이벤트만 공유한다 — 서로의 변수는 못 본다.
================================================== */

function announceTokenToExtension() {
  if (!token || token === DEMO_TOKEN || tokenExpired(token)) return;
  try {
    window.dispatchEvent(new CustomEvent('underfaker-jwt-captured', { detail: token }));
  } catch (e) { /* 구형 브라우저 — 무시 */ }
}

function announceLogoutToExtension() {
  try {
    window.dispatchEvent(new CustomEvent('underfaker-jwt-cleared'));
  } catch (e) { /* noop */ }
}

/* 확장(0.3.2 이상)은 document_start 에 <html data-underfaker-extension="버전"> 을 붙인다.
   없으면 미설치이거나 0.3.1 이하. */
function extensionVersion() {
  const v = document.documentElement.getAttribute('data-underfaker-extension');
  return v && v.trim() ? v.trim() : null;
}

/* 10/12 — 확장 0.5.0 부터 <html data-underfaker-features="scan,url-read"> 를 붙인다.
   scan     = 쿠팡 구매이력 '최근 N건 읽기'·'구매일자 읽기' 버튼
   url-read = 품명 확인 '쿠팡 URL 붙여넣기' 자동 기입
   capture  = 품명 확인 '쇼핑몰 화면에서 드래그'(0.6.0 — 우클릭 메뉴로 영역의 글·사진 가져오기) */
function extensionSupports(feature) {
  const f = document.documentElement.getAttribute('data-underfaker-features') || '';
  return f.split(',').map(function (x) { return x.trim(); }).indexOf(feature) >= 0;
}

function paintExtensionStatus() {
  const el = document.getElementById('extensionStatus');
  paintScanControls();
  if (!el) return;

  const v = extensionVersion();
  if (v && !extensionSupports('scan')) {
    /* 10/12 — 0.4.x 는 연결은 되지만 읽기 버튼(0.5.0)을 못 쓴다 */
    el.className = 'extension-status missing';
    el.textContent = '확장 프로그램 v' + v + ' 연결됨 — 아래 읽기 버튼은 0.5.0 이상에서 됩니다. ' +
        'chrome://extensions 에서 새 버전 폴더를 다시 불러온 뒤 이 페이지를 새로고침하세요.';
  } else if (v) {
    el.className = 'extension-status ok';
    el.textContent = '✓ 확장 프로그램 연결됨 (v' + v + ')';
  } else {
    el.className = 'extension-status missing';
    el.textContent = '확장 프로그램 미감지 — chrome://extensions 에서 설치(0.5.0 이상) 후 이 페이지 새로고침';
  }
}


/* ==================================================
   입력 편의
================================================== */

function bindEnterKeys() {
  const onEnter = function (ids, fn) {
    ids.forEach(function (id) {
      const el = document.getElementById(id);
      if (!el) return;
      el.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && !e.isComposing) {
          e.preventDefault();
          fn();
        }
      });
    });
  };

  onEnter(['productName', 'brandName', 'modelName', 'makerName', 'barcodeNum', 'certNum', 'thumbnailUrl'], verify);
  onEnter(['email', 'password'], login);
}

/* '자주 확인하는 제품' 칩 — 이모지를 뺀 글자를 제품명 칸에 넣는다. 바로 검증하지는 않는다. */
function bindCategoryChips() {
  document.querySelectorAll('.categories .category').forEach(function (chip) {
    chip.addEventListener('click', function () {
      const label = (chip.textContent || '')
          .replace(/[^\p{L}\p{N}\s]/gu, '')
          .trim();
      const input = document.getElementById('productName');
      if (!input || !label) return;
      input.value = label;
      input.focus();
      clearMsg('verifyMsg');
    });
  });
}


/* ==================================================
   HEADER
================================================== */

function paintHeader() {

  const authBtn = document.getElementById('authBtn');
  const profileBtn = document.getElementById('profileBtn');

  if (authBtn) {
    authBtn.textContent = token ? '로그아웃' : '로그인/회원가입';
    authBtn.title = authBtn.textContent;
  }

  if (profileBtn) {
    profileBtn.title = token
        ? (userEmail ? userEmail + ' 님' : '내 검증 이력')
        : '로그인';
  }

  updateLoginMessages();
}


/* ==================================================
   LOGIN MESSAGE
   기존 코드는 if/else 양쪽이 verifyMsg 를 똑같이 display:none 으로 두고 있었다.
   verifyMsg 는 verify() 가 비로그인 상태를 감지할 때만 켜는 요소라서
   여기서는 항상 숨긴 채로 둔다.
================================================== */

function updateLoginMessages() {

  const loggedIn = Boolean(token);

  hide('verifyLoginMsg');

  toggle('historyLoginRequired', !loggedIn);
  toggle('historyContent', loggedIn);

  toggle('coupangLoginRequired', !loggedIn);
  toggle('coupangContent', loggedIn);
}

function toggle(id, on) {
  const el = document.getElementById(id);
  if (el) el.style.display = on ? 'block' : 'none';
}

function hide(id) {
  toggle(id, false);
}


/* ==================================================
   NAV
================================================== */

function handleProfileClick() {
  if (token) goHistory();
  else showScreen('loginScreen');
}

function handleAuthButton() {
  if (token) logout();
  else showScreen('loginScreen');
}


/* ==================================================
   MESSAGE
================================================== */

function show(id, text, ok) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = text;
  el.className = 'msg ' + (ok ? 'ok' : 'err');
}

/* .msg.ok / .msg.err 만 display:block 이다. 빈 문자열로 show() 를 부르면
   내용 없는 초록 띠가 남으므로, 지울 때는 클래스를 떼야 한다. */
function clearMsg(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = '';
  el.className = 'msg';
}


/* ==================================================
   HEADERS
   demo-token 은 절대 서버로 보내지 않는다. 백엔드 JWT 필터가
   형식 불량 토큰으로 401 을 내면서 로그가 더러워지고,
   더미 토큰이 실서버에 날아가는 것 자체가 바람직하지 않다.
================================================== */

function headers() {

  const h = { 'Content-Type': 'application/json' };

  if (token && token !== DEMO_TOKEN) {
    h['Authorization'] = 'Bearer ' + token;
  }

  return h;
}


/* ==================================================
   INPUT VALUE
================================================== */

function val(id) {
  const el = document.getElementById(id);
  if (!el) return null;
  const v = el.value.trim();
  return v === '' ? null : v;
}


/* ==================================================
   API CALL
================================================== */

async function call(path, method, body) {

  if (DEMO_MODE) {
    /* 더미 모드에서는 네트워크를 타지 않는다. 호출이 새는지 바로 보이게 로그만 남긴다. */
    console.warn('[DEMO_MODE] API 호출 차단:', method, path);
    return { status: 0, json: { success: false, message: '더미 모드입니다.' } };
  }

  try {

    const res = await fetch(API + path, {
      method: method,
      headers: headers(),
      body: body ? JSON.stringify(body) : undefined
    });

    let json = null;

    try {
      json = await res.json();
    } catch (e) {
      json = null;
    }

    if (res.status === 401) {
      handleUnauthorized();
    }

    return { status: res.status, json: json };

  } catch (error) {

    console.error('API 호출 오류:', error);

    return {
      status: 0,
      json: { success: false, message: '서버에 연결할 수 없습니다.' }
    };
  }
}

function handleUnauthorized() {

  token = null;
  userEmail = null;
  announceLogoutToExtension();
  stopCoupangPolling();

  try {
    localStorage.removeItem('rc_token');
    localStorage.removeItem('rc_email');
  } catch (e) { /* noop */ }

  paintHeader();
}


/* ==================================================
   SCREEN
================================================== */

function showScreen(id) {

  document.querySelectorAll('.screen')
      .forEach(el => el.classList.remove('active'));

  const target = document.getElementById(id);
  if (target) target.classList.add('active');

  document.querySelectorAll('.nav-item')
      .forEach(el => el.classList.remove('active'));

  const navMap = {
    verifyScreen: 'navVerify',
    procedureScreen: 'navProcedure',
    coupangScreen: 'navCoupang'
  };

  const nav = document.getElementById(navMap[id]);
  if (nav) nav.classList.add('active');

  /* 화면을 옮길 때마다 결과 패널은 접는다. 기존 코드는 .show 를 붙이기만 하고 떼지 않아서
     다른 화면으로 갔다 돌아오면 이전 결과가 남아 있었다.
     결과를 보여 줄 쪽(verify, openHistoryItem)이 showScreen 뒤에 다시 연다. */
  const resultPanel = document.getElementById('resultScreen');
  if (resultPanel) resultPanel.classList.remove('show');

  updateLoginMessages();

  if (id === 'coupangScreen') {
    paintExtensionStatus();
    if (token) startCoupangPolling();
  } else {
    stopCoupangPolling();
  }

  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function goHome() {
  showScreen('verifyScreen');
}

function goProcedure() {
  showScreen('procedureScreen');
}


/* ==================================================
   HISTORY DROPDOWN
   #historyMenuWrap 은 현재 마크업에 없다. 없어도 안 터지게 방어만 해 둔다.
================================================== */

function toggleHistoryMenu(event) {
  if (event) event.stopPropagation();
  const wrap = document.getElementById('historyMenuWrap');
  if (wrap) wrap.classList.toggle('open');
}

function closeHistoryMenu() {
  const wrap = document.getElementById('historyMenuWrap');
  if (wrap) wrap.classList.remove('open');
}

document.addEventListener('click', function (event) {
  const wrap = document.getElementById('historyMenuWrap');
  if (wrap && !wrap.contains(event.target)) closeHistoryMenu();
});


/* ==================================================
   HISTORY / COUPANG 화면 이동
================================================== */

function goHistory() {
  showScreen('historyScreen');
  if (!token) return;
  showHistoryType('manual');
}

function goCoupangHistory() {
  showScreen('coupangScreen');
}

function openCoupang() {
  window.open(
      'https://mc.coupang.com/ssr/desktop/order/list',
      '_blank',
      'noopener,noreferrer'
  );
}

function goCoupangVerificationHistory() {
  showScreen('historyScreen');
  if (!token) return;
  showHistoryType('coupang');
}


/* ==================================================
   SIGNUP / LOGIN / LOGOUT
================================================== */

async function signup() {

  if (DEMO_MODE) {
    show('authMsg', '더미 모드에서는 회원가입을 호출하지 않습니다.', false);
    return;
  }

  const r = await call('/api/auth/signup', 'POST', {
    email: val('email'),
    password: val('password'),
    username: val('username')
  });

  if (r.json && r.json.success) {
    show('authMsg', '회원가입 완료. 이제 로그인하세요.', true);
  } else {
    show('authMsg',
        '회원가입 실패 (' + r.status + ') ' + (r.json?.message || ''),
        false);
  }
}


async function login() {

  if (DEMO_MODE) {

    token = DEMO_TOKEN;
    userEmail = val('email') || 'demo@example.com';

    paintHeader();
    show('authMsg', '더미 로그인되었습니다.', true);

    setTimeout(function () { showScreen('verifyScreen'); }, 150);
    return;
  }

  const r = await call('/api/auth/login', 'POST', {
    email: val('email'),
    password: val('password')
  });

  if (r.json && r.json.success && r.json.data && r.json.data.accessToken) {

    token = r.json.data.accessToken;
    userEmail = val('email');

    try {
      localStorage.setItem('rc_token', token);
      localStorage.setItem('rc_email', userEmail);
    } catch (e) { /* noop */ }

    announceTokenToExtension();

    paintHeader();
    show('authMsg', '로그인되었습니다.', true);

    setTimeout(function () { showScreen('verifyScreen'); }, 300);

  } else {
    show('authMsg',
        '로그인 실패 (' + r.status + ') ' + (r.json?.message || ''),
        false);
  }
}


function logout() {

  token = DEMO_MODE ? DEMO_TOKEN : null;
  userEmail = DEMO_MODE ? 'demo@example.com' : null;

  try {
    localStorage.removeItem('rc_token');
    localStorage.removeItem('rc_email');
  } catch (e) { /* noop */ }

  lastInput = emptyInput();

  if (!DEMO_MODE) announceLogoutToExtension();
  stopCoupangPolling();

  paintHeader();
  showScreen('verifyScreen');
}


/* ==================================================
   10/12 — 품명으로 제품 확인: ① 직접 입력 / ② 쿠팡 URL 붙여넣기 (멘토링 피드백)
   ② 는 확장(0.5.0)이 그 상품 상세페이지를 보이지 않는 탭으로 읽어 값을 돌려준다(서버에서 쿠팡을 열면 403).
   채운 값은 그대로 고칠 수 있고, '리콜 여부 확인하기'는 두 방식 모두 같은 버튼이다.
================================================== */

var inputMode = 'manual';
var urlReadExtras = null;   // { kcStatus, kcText } — 마지막으로 URL 에서 읽은 KC 칸
var urlReadPending = null;  // { requestId, timer }

const URL_READ_ERROR_TEXT = {
  NOT_COUPANG_PRODUCT: '쿠팡 상품 페이지 주소만 읽을 수 있습니다. (예: https://www.coupang.com/vp/products/…)',
  TIMEOUT: '상품 페이지가 제때 열리지 않았습니다. 잠시 후 다시 눌러 주세요.',
  UNREADABLE: '상품 페이지를 읽지 못했습니다(쿠팡 차단·구조 변경 가능). 직접 입력으로 넣어 주세요.',
  CONTEXT_LOST: '확장 프로그램이 새로고침돼 연결이 끊겼습니다. 이 페이지를 새로고침(F5)해 주세요.',
  NO_EXTENSION: '확장 프로그램(0.5.0 이상)이 있어야 URL 로 불러올 수 있습니다. 설치가 어려우면 ① 직접 입력을 쓰세요.',
  BUSY: '확장 프로그램이 쿠팡 구매이력을 읽는 중이라 지금은 불러올 수 없습니다. 끝난 뒤 다시 눌러 주세요.',
  SLOW: '응답이 너무 늦습니다. 잠시 후 다시 누르거나 ① 직접 입력을 쓰세요.'
};

/* URL 로 채우는 칸 — 새로 불러올 때 앞 상품 값이 남지 않게 먼저 비운다 */
const URL_FILL_FIELDS = ['productName', 'modelName', 'makerName', 'certNum', 'thumbnailUrl'];

function setInputMode(mode) {
  const prev = inputMode;
  inputMode = (mode === 'url' || mode === 'capture') ? mode : 'manual';
  const manualBtn = document.getElementById('modeManualBtn');
  const urlBtn = document.getElementById('modeUrlBtn');
  const captureBtn = document.getElementById('modeCaptureBtn');
  if (manualBtn) manualBtn.classList.toggle('active', inputMode === 'manual');
  if (urlBtn) urlBtn.classList.toggle('active', inputMode === 'url');
  if (captureBtn) captureBtn.classList.toggle('active', inputMode === 'capture');
  const box = document.getElementById('urlReadBox');
  if (box) box.style.display = inputMode === 'url' ? '' : 'none';
  const cbox = document.getElementById('captureBox');
  if (cbox) cbox.style.display = inputMode === 'capture' ? '' : 'none';
  /* 다른 방식으로 바꾸면 그 방식에서 따로 들고 있던 KC 칸 원문은 버린다(칸에 채운 값은 그대로 둔다) */
  if (prev !== inputMode) {
    if (prev === 'url') urlReadExtras = null;
    if (prev === 'capture') resetCaptureState();
  }
  if (inputMode === 'capture') {
    paintCaptureIntro();
    return;
  }
  if (inputMode === 'manual') {
    urlReadExtras = null;
    setUrlReadMsg('', 'idle');
  } else if (!extensionSupports('url-read')) {
    setUrlReadMsg(URL_READ_ERROR_TEXT.NO_EXTENSION, 'err');
  } else {
    setUrlReadMsg('쿠팡 상품 페이지 주소를 붙여 넣고 [불러오기]를 누르세요. 확장 프로그램이 상품명·모델명·제조사·KC 인증번호·사진 주소를 채웁니다.', 'idle');
  }
}

function setUrlReadMsg(message, kind) {
  const el = document.getElementById('urlReadMsg');
  if (!el) return;
  el.textContent = message || '';
  el.className = 'url-read-msg ' + (kind || 'idle');
}

function readProductUrl() {
  const url = val('productUrl');
  if (!url) {
    setUrlReadMsg('쿠팡 상품 페이지 주소를 붙여 넣어 주세요.', 'err');
    return;
  }
  if (!/^https?:\/\/([a-z0-9-]+\.)*coupang\.com\//i.test(url)) {
    setUrlReadMsg(URL_READ_ERROR_TEXT.NOT_COUPANG_PRODUCT, 'err');
    return;
  }
  if (!extensionSupports('url-read')) {
    setUrlReadMsg(URL_READ_ERROR_TEXT.NO_EXTENSION, 'err');
    return;
  }
  if (urlReadPending) return;
  /* 앞에서 불러온 상품의 KC 칸이 이번 상품 확인 요청에 실리지 않게 지운다 */
  urlReadExtras = null;
  const requestId = 'u' + Date.now();
  const btn = document.getElementById('urlReadBtn');
  if (btn) {
    btn.disabled = true;
    btn.textContent = '불러오는 중…';
  }
  setUrlReadMsg('상품 페이지를 읽는 중입니다(보이지 않는 탭에서 5~20초)…', 'busy');
  urlReadPending = {
    requestId: requestId,
    timer: setTimeout(function () { finishUrlRead({ requestId: requestId, ok: false, error: 'SLOW' }); }, 45000)
  };
  window.dispatchEvent(new CustomEvent('underfaker-url-read', { detail: JSON.stringify({ requestId: requestId, url: url }) }));
}

function finishUrlRead(res) {
  if (!urlReadPending || !res || res.requestId !== urlReadPending.requestId) return;
  clearTimeout(urlReadPending.timer);
  urlReadPending = null;
  const btn = document.getElementById('urlReadBtn');
  if (btn) {
    btn.disabled = false;
    btn.textContent = '불러오기';
  }
  if (!res.ok || !res.info) {
    setUrlReadMsg(URL_READ_ERROR_TEXT[res.error] || ('불러오지 못했습니다 (' + res.error + ').'), 'err');
    return;
  }
  const info = res.info;
  const filled = [];
  URL_FILL_FIELDS.forEach(function (id) {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });
  function put(id, value, label) {
    const el = document.getElementById(id);
    if (!el || !value) return;
    el.value = value;
    filled.push(label);
  }
  put('productName', info.title, '제품명');
  put('modelName', info.modelName, '모델명');
  put('makerName', info.makerName, '제조사');
  put('certNum', info.certNum, 'KC 인증번호');
  if (info.image && !thumbnailUrlProblem(info.image)) put('thumbnailUrl', info.image, '사진 주소');
  urlReadExtras = { kcStatus: info.kcStatus || null, kcText: info.kcText || null };

  const kcNote = info.certNum ? ''
      : (info.kcStatus === 'NONE' ? ' KC 칸이 없거나 KC 대상이 아니라고 표기된 상품입니다.'
          : ' KC 인증정보 칸에 번호가 없습니다' + (info.kcText ? '("' + info.kcText + '")' : '') + '.');
  setUrlReadMsg((filled.length ? '채운 칸: ' + filled.join('·') + '.' : '채울 값을 찾지 못했습니다.') + kcNote +
      ' 값을 확인·수정한 뒤 [리콜 여부 확인하기]를 누르세요.', filled.length ? 'ok' : 'err');
}

window.addEventListener('underfaker-url-result', function (e) {
  let res = null;
  try { res = typeof e.detail === 'string' ? JSON.parse(e.detail) : null; } catch (err) { res = null; }
  finishUrlRead(res);
});


/* ==================================================
   10/12 — ③ 쇼핑몰 화면에서 드래그 (확장 0.6.0)
   쇼핑몰 상품 페이지에서 우클릭 → UnderFaker → 영역 드래그. 확장이 그 안의 글(또는 사진 주소)을 읽고
   칸(제품명·모델명·제조사·KC 인증번호)을 나눠 이 화면에 보낸다(underfaker-capture).
   같은 상품 페이지에서 여러 번 가져오면(상품명 → 정보 표 → 사진) 찾은 칸만 덮어 쓴다.
   다른 상품 페이지에서 가져오면 앞 상품 값이 남지 않게 먼저 비운다.
   이 화면이 닫혀 있을 때 가져온 값은 확장이 맡아 뒀다가, 화면이 뜰 때 넘겨준다(underfaker-capture-pull).
================================================== */

var captureExtras = null;   // { kcStatus, kcText } — 가져온 KC 칸 원문(서버가 번호를 다시 뽑는다)
var captureSourceKey = null; // 마지막으로 가져온 상품 페이지(주소에서 # 뒤를 뺀 것)
var captureSeen = {};        // 이미 넣은 가져오기 id — 같은 값이 두 번 와도 한 번만

const CAPTURE_FIELD_LABEL = { productName: '제품명', modelName: '모델명', makerName: '제조사', certNum: 'KC 인증번호' };

function setCaptureMsg(message, kind) {
  const el = document.getElementById('captureMsg');
  if (!el) return;
  el.textContent = message || '';
  el.className = 'url-read-msg ' + (kind || 'idle');
}

/* ③ 에서 다른 방식으로 바꿀 때 — KC 칸 원문만 버린다. 마지막 상품 페이지는 기억해서,
   나중에 다른 상품을 가져오면 그때 앞 상품 값을 비운다 */
function resetCaptureState() {
  captureExtras = null;
}

function paintCaptureIntro() {
  if (!extensionSupports('capture')) {
    setCaptureMsg('확장 프로그램 0.6.0 이상이 있어야 쇼핑몰 화면에서 가져올 수 있습니다. ' +
        'chrome://extensions 에서 새 버전 폴더를 다시 불러온 뒤 이 페이지를 새로고침하세요.', 'err');
    return;
  }
  if (!captureSourceKey) {
    setCaptureMsg('아직 가져온 값이 없습니다. 위 순서대로 쇼핑몰 화면에서 가져오면 아래 칸이 자동으로 채워집니다.', 'idle');
  }
  pullPendingCaptures();
}

function pullPendingCaptures() {
  try {
    window.dispatchEvent(new CustomEvent('underfaker-capture-pull'));
  } catch (e) { /* noop */ }
}

function captureKey(url) {
  return typeof url === 'string' ? url.split('#')[0] : '';
}

function flashFilled(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.classList.add('just-filled');
  setTimeout(function () { el.classList.remove('just-filled'); }, 2500);
}

/** @returns {boolean} 받아서 칸에 넣었는가 */
function onCapture(c) {
  if (!c || typeof c !== 'object' || (c.kind !== 'text' && c.kind !== 'image')) return false;
  if (c.id) {
    if (captureSeen[c.id]) return true;
    captureSeen[c.id] = true;
  }
  showScreen('verifyScreen');
  if (inputMode !== 'capture') setInputMode('capture');

  const key = captureKey(c.pageUrl);
  if (key && captureSourceKey && key !== captureSourceKey) {
    /* 다른 상품 페이지 — 앞 상품 값이 섞이지 않게 비운다 */
    URL_FILL_FIELDS.forEach(function (id) {
      const el = document.getElementById(id);
      if (el) el.value = '';
    });
    captureExtras = null;
  }
  if (key) captureSourceKey = key;

  const filled = [];
  function put(id, value, label) {
    const el = document.getElementById(id);
    if (!el || !value) return;
    el.value = String(value).slice(0, 255);
    filled.push(label);
    flashFilled(id);
  }

  if (c.kind === 'image') {
    const problem = thumbnailUrlProblem(c.imageUrl);
    if (problem) {
      setCaptureMsg('사진을 넣지 못했습니다. ' + problem, 'err');
      return true;
    }
    const el = document.getElementById('thumbnailUrl');
    if (el) {
      el.value = c.imageUrl;
      flashFilled('thumbnailUrl');
    }
    filled.push('사진 주소');
  } else {
    const f = c.fields || {};
    Object.keys(CAPTURE_FIELD_LABEL).forEach(function (k) {
      put(k, f[k], CAPTURE_FIELD_LABEL[k]);
    });
    if (f.kcText || f.certNum) {
      captureExtras = { kcStatus: f.kcStatus || null, kcText: f.kcText || null };
    }
    const pre = document.getElementById('capturePreview');
    if (pre) pre.textContent = c.text || '';
    const src = document.getElementById('captureSource');
    if (src) src.textContent = '가져온 페이지: ' + (c.pageTitle || '') + (c.pageUrl ? ' (' + c.pageUrl + ')' : '');
    const box = document.getElementById('capturePreviewBox');
    if (box) box.style.display = '';
  }

  const missing = [];
  if (!val('productName')) missing.push('제품명');
  if (!val('certNum')) missing.push('KC 인증번호');
  if (!val('thumbnailUrl')) missing.push('사진');
  let msg = filled.length
      ? '채운 칸: ' + filled.join('·') + '.'
      : '가져온 글에서 제품명·모델명·제조사·KC 칸을 찾지 못했습니다. 아래 \'가져온 글 보기\'에서 복사해 넣어 주세요.';
  if (missing.length) msg += ' 아직 빈 칸: ' + missing.join('·') + ' — 같은 메뉴로 더 가져오거나 직접 입력하세요.';
  else msg += ' 값을 확인·수정한 뒤 [리콜 여부 확인하기]를 누르세요.';
  setCaptureMsg(msg, filled.length ? 'ok' : 'err');
  return true;
}

window.addEventListener('underfaker-capture', function (e) {
  let c = null;
  try { c = typeof e.detail === 'string' ? JSON.parse(e.detail) : null; } catch (err) { c = null; }
  /* 받았다고 확장에 알린다 — 안 받으면 확장이 맡아 두고 새로고침을 안내한다 */
  if (onCapture(c)) e.preventDefault();
});


/* ==================================================
   VERIFY
================================================== */

async function verify() {

  if (!token) {
    const msg = document.getElementById('verifyLoginMsg');
    if (msg) {
      msg.style.display = 'block';
      msg.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
    return;
  }

  const productName = val('productName');

  if (!productName) {
    show('verifyMsg', '제품명을 입력해주세요.', false);
    return;
  }

  const thumbnailUrl = val('thumbnailUrl');
  const thumbProblem = thumbnailUrlProblem(thumbnailUrl);
  if (thumbProblem) {
    show('verifyMsg', thumbProblem, false);
    return;
  }

  const btn = document.getElementById('verifyBtn');
  if (btn) {
    btn.disabled = true;
    btn.textContent = '확인하는 중...';
  }

  try {

    /* 입력값을 여기서 붙잡아 둔다. 서버 응답에는 입력 쪽 필드가 없다. */
    lastInput = {
      productName: productName,
      brandName: val('brandName'),
      modelName: val('modelName'),
      makerName: val('makerName'),
      barcodeNum: val('barcodeNum'),
      certNum: val('certNum'),
      thumbnailUrl: thumbnailUrl
    };
    /* 10/12 — 쿠팡 URL 로 불러왔거나 쇼핑몰 화면에서 가져왔으면 KC 칸 원문도 같이 보낸다. KC 번호가 여러 개면 서버가 원문에서 모두 뽑아
       조회·대조한다(KcCertNumbers.joinAll). 직접 입력 모드로 바꾸면 비운다. */
    const extras = inputMode === 'url' ? urlReadExtras : (inputMode === 'capture' ? captureExtras : null);
    if (extras) {
      lastInput.kcStatus = extras.kcStatus || null;
      lastInput.kcText = extras.kcText || null;
    }

    let result = null;

    if (DEMO_MODE) {

      result = createDemoManualResult(lastInput);

    } else {

      const r = await call('/api/verifications/manual', 'POST', lastInput);

      if (!r.json || !r.json.success) {
        show('verifyMsg',
            '검증 실패 (' + r.status + ') ' +
            (r.json?.message || '알 수 없는 오류가 발생했습니다.'),
            false);
        return;
      }

      result = r.json.data;
    }

    clearMsg('verifyMsg');

    renderInput(lastInput);
    render(result);

    const panel = document.getElementById('resultScreen');
    if (panel) panel.classList.add('show');

    if (result && result.verificationId) {
      loadEvidence(result.verificationId);
    }

    if (panel) panel.scrollIntoView({ behavior: 'smooth', block: 'start' });

  } finally {
    if (btn) {
      btn.disabled = false;
      btn.textContent = '🔍 리콜 여부 확인하기';
    }
  }
}


/* ==================================================
   LABEL
================================================== */

/* 9/27 — 팀장 결정 4단계: 일치(100% 확정 근거) · 의심 · 항목누락 · 불일치.
   서버가 resultState 로 내려준다(dto.internal.ResultView). MISSING 은 DB 판정값이 아니라
   "텍스트로 못 찾았고 KC 인증번호도 없음" 을 뜻하는 화면 상태다. 없으면 finalResult 로 대신한다.
   10/10 — 최종 흐름(팀장 결정): MISSING 표시를 '항목누락' → 'KC 번호 미표기'로 바꾼다. '불법'으로 단정하지 않는다
   (공급자적합성확인 제품은 인증기관 번호가 없을 수 있다). EXCLUDED(판단 제외) 추가 — (어린이제품이 아닌) 해외직구·구매대행 상품은 판단하지 않는다. */
const LABEL = {
  MATCH: '일치',
  PARTIAL: '의심',
  MISSING: 'KC 번호 미표기',
  NO_MATCH: '불일치',
  UNKNOWN: '확인불가',
  EXCLUDED: '판단 제외'
};

const SUBLABEL = {
  MATCH: '리콜 대상 제품과 일치합니다 (인증번호·모델명 등 100% 일치).',
  PARTIAL: '리콜 제품과 닮았습니다. 확인이 필요합니다.',
  MISSING: 'KC 인증번호가 없어 KC 번호로 판단하지 못했습니다.',
  NO_MATCH: '일치하는 리콜 공표문을 찾지 못했습니다. 안전하다는 뜻은 아닙니다.',
  UNKNOWN: '정보 부족으로 판별할 수 없습니다.',
  EXCLUDED: '(어린이제품이 아닌) 해외직구·구매대행 상품은 판단하지 않습니다.'
};

/* 화면에 쓸 상태 키 — 서버 resultState 우선, 없으면(구 서버·더미) finalResult */
function stateOf(v) {
  return (v && (v.resultState || v.finalResult)) || 'UNKNOWN';
}

/* MatchEvidenceResponse.comparisons[].field → 화면 라벨.
   MatchingService.korean(String field) 의 switch 와 문자열까지 동일하게 맞췄다.
   imageLabel 은 2단계 Vision 판독 결과 행이다. */
const FIELD_LABEL = {
  modelName: '모델명',
  certNum: '인증번호',
  productName: '제품명',
  makerName: '제조사',
  brandName: '브랜드',
  imageLabel: '이미지판독',
  certModelName: '인증 모델명',  /* 10/3 — KC 인증 DB 모델명 대조. 완전일치일 때만 판정에 반영 */
  /* 10/10 — 사진 찾기(2차): 같은 사진이 실린 서로 다른 사이트 2곳 이상에서 같은 값. 최대 '의심'까지만 */
  imageCertNum: '사진 KC 번호',
  imageModelName: '사진 모델명'
};

/* comparisons[].field → 입력 비교 박스의 element id */
const INPUT_FIELD_EL = {
  productName: 'inputProduct',
  brandName: 'inputBrand',
  modelName: 'inputModel'
};

/* VerificationStatus = PENDING(처리 대기) / DONE(처리 완료) / FAILED(처리 실패).
   DONE 이 아니면 이력 항목에 상태 뱃지를 따로 찍는다 — 판정 결과가 없는 건인데
   '확인불가'로만 보이면 서버가 실패한 것인지 정보가 부족한 것인지 구분이 안 된다. */
const STATUS_OK = 'DONE';

const STATUS_LABEL = {
  PENDING: '처리 중',
  FAILED: '처리 실패'
};


/* ==================================================
   RENDER — 입력 쪽
================================================== */

function renderInput(input) {

  const i = input || emptyInput();

  text('inputProduct', i.productName);
  text('inputBrand', i.brandName);
  text('inputModel', i.modelName);
  text('inputBarcode', i.barcodeNum);
}


/* ==================================================
   RENDER — 공표문 쪽
   VerificationResultResponse 필드명 기준.
================================================== */

function render(d) {

  if (!d) return;

  const fr = stateOf(d);

  setResultState(fr, d.similarityScore, d);

  text('recallProduct', d.recallProductName);
  text('recallBrand', d.recallBrandName);
  text('recallModel', d.recallModelName);
  /* 9/27 — maker_name 은 실데이터에서 거의 비어 있다. 백엔드 Recall.resolveMakerName() 과 같은 규칙으로
     recall_cmpny_name 을 대신 보여 준다(판정도 그 값으로 한다). */
  text('recallMaker', d.makerName || d.recallCmpnyName);  /* 구 recallBarcode 자리 */

  text('recallType', d.recallTypeName);      /* 구 d.recallType */
  text('recallMethod', d.recallMeans);       /* 구 d.recallMethod */
  text('recallBusiness', d.recallCmpnyName); /* 구 d.recallBusiness */
  text('recallDate', fmtDate(d.publishDate));/* 구 d.recallDate, yyyyMMdd */
  text('recallDefect', d.harmDscr);          /* 구 d.recallDefect */
  text('recallRisk', d.accidentCaseDscr);    /* 구 d.recallRisk */
  text('recallAction', d.publishActionDscr); /* 신규 — 소비자 행동요령 */
}

function text(id, v) {
  const el = document.getElementById(id);
  if (!el) return;
  const s = (v === null || v === undefined || v === '') ? '-' : String(v);
  el.textContent = s;
}

/* publishDate 는 yyyyMMdd 문자열이다. 이미 하이픈이 있으면 그대로 둔다. */
function fmtDate(s) {
  if (!s) return null;
  const v = String(s).trim();
  if (/^\d{8}$/.test(v)) {
    return v.substring(0, 4) + '-' + v.substring(4, 6) + '-' + v.substring(6, 8);
  }
  return v;
}

function fmtDateTime(s) {
  if (!s) return '';
  return String(s).replace('T', ' ').substring(0, 19);
}


/* ==================================================
   RESULT STATE
================================================== */

function setResultState(fr, score, d) {

  const hero = document.getElementById('resultHero');
  if (hero) hero.className = 'result-hero state-' + fr;

  /* 9/30 — 불일치 아이콘 ✓ → ≠ . 체크 표시는 '안전 확인'으로 읽힌다(불일치 = 공표문과 안 맞음일 뿐) */
  const icons = { MATCH: '!', PARTIAL: '?', MISSING: '…', NO_MATCH: '≠', UNKNOWN: '–', EXCLUDED: '–' };

  text('resultIcon', icons[fr] || '–');
  text('resultTitle', LABEL[fr] || '확인불가');
  text('resultSub', SUBLABEL[fr] || SUBLABEL.UNKNOWN);

  /* 10/7 — 불일치·항목누락이면 종합 유사도를 숨긴다. 이 숫자는 '가장 가까운 후보'의 점수라서
     3.6% 같은 무관한 공표문 점수가 결과처럼 보였다(10/7 배밀이 쿠션 ↔ HP 노트북 배터리). */
  const scoreEl = document.getElementById('score');
  if (scoreEl) {
    scoreEl.textContent =
            (score === null || score === undefined || fr === 'NO_MATCH' || fr === 'MISSING' || fr === 'EXCLUDED')
                    ? '-'
                    : (score * 100).toFixed(1) + '%';
  }

  document.querySelectorAll('.judgement-state')
      .forEach(el => el.classList.remove('active'));

  const stateMap = {
    MATCH: 'stateMatch',
    PARTIAL: 'statePartial',
    MISSING: 'stateMissing',
    NO_MATCH: 'stateNoMatch'
  };

  const activeState = document.getElementById(stateMap[fr]);
  if (activeState) activeState.classList.add('active');

  const notice = document.getElementById('resultNotice');
  if (!notice) return;

  if (fr === 'MATCH') {
    notice.className = 'result-notice match';
    notice.innerHTML =
        '<strong>⚠ 리콜 대상 제품입니다.</strong><br>' +
        '제품 사용을 중지하고 아래 <strong>소비자 행동요령</strong>을 확인하세요.';

  } else if (fr === 'PARTIAL') {
    notice.className = 'result-notice partial';
    notice.innerHTML =
        '<strong>🔎 리콜 의심 제품입니다.</strong><br>' +
        '제품명이나 브랜드가 리콜 공표문과 닮았지만 인증번호·모델명으로 100% 확인되지는 않았습니다. ' +
        '아래 판정 근거의 항목별 유사도를 확인하세요.';

  } else if (fr === 'MISSING') {
    notice.className = 'result-notice missing';
    /* 10/7 — 사진 확인 뒤에도 KC 번호 미표기(구 항목누락)로 남는다(ResultView). 이미 사진으로 찾아봤으면 그 사실을 적는다. */
    const imageChecked = d && (d.imageCheck === 'FOUND' || d.imageCheck === 'NONE');
    notice.innerHTML =
            '<strong>🟣 KC 번호 미표기 — KC 인증번호로 판단할 수 없습니다.</strong><br>' +
            esc((d && d.missingReason) || 'KC 인증번호가 없습니다.') + ' ' +
            (imageChecked
                    ? '사진으로도 찾아봤지만 일치하는 리콜 공표문은 없었습니다. ' +
                      '공표문과 맞지 않는다고 KC 인증을 받은 제품이라는 뜻은 아닙니다.'
                    : '상품명만으로는 리콜 공표문을 찾지 못했습니다. ' +
                      /* 10/10 — 사진 주소가 없으면 사진 찾기 버튼이 안 나온다(웹 직접입력은 대개 사진 없음) */
                      (d && d.imageCheckAvailable
                              ? '사진으로 한 번 더 찾아볼 수 있습니다.'
                              : 'KC 인증번호를 넣거나 사진 주소를 넣으면 다시 확인할 수 있습니다.'));

  } else if (fr === 'EXCLUDED') {
    /* 10/10 — 국외 판매(해외직구·구매대행) 상품은 판단 범위가 아니다(팀장 결정). 서버 ResultView.EXCLUDED */
    notice.className = 'result-notice unknown';
    notice.innerHTML =
        '<strong>– 판단 제외 — (어린이제품이 아닌) 해외직구·구매대행 상품</strong><br>' +
        esc((d && d.missingReason) || '(어린이제품이 아닌) 해외직구·구매대행 상품이라 판단하지 않습니다.') + ' ' +
        '이 서비스는 국내에서 판매되는 KC 인증 대상 공산품·유아제품만 판단합니다. ' +
        /* 10/10 — 어린이제품은 구매대행도 KC 표시 필수(어린이제품법 제30조)라 서버가 판단 제외하지 않는다 */
        '어린이제품은 해외직구·구매대행이어도 KC 표시가 필수라 판단합니다(어린이제품 안전 특별법 제30조).';

  } else if (fr === 'NO_MATCH') {
    notice.className = 'result-notice nomatch';
    notice.innerHTML =
        '<strong>일치하는 리콜 공표문을 찾지 못했습니다.</strong><br>' +
        '현재 입력한 정보 기준으로 공식 리콜 정보와 일치하지 않습니다. ' +
        '단, 안전하다는 뜻이 아니며 리콜 대상이 아님을 의미하지도 않습니다.';

  } else {
    notice.className = 'result-notice unknown';
    notice.innerHTML =
        '<strong>– 확인할 수 없습니다.</strong><br>' +
        '정보가 부족하거나 공식 데이터를 조회하지 못했습니다. ' +
        '제품 정보를 추가한 후 다시 확인해주세요.';
  }

  notice.innerHTML += kcCertHtml(d);
  notice.innerHTML += imageCheckHtml(d);
}

/* 10/3 — KC인증 연동. 입력한 KC 인증번호로 KC 인증 DB(제품안전정보센터)를 조회한 결과.
   리콜 판정(일치/의심/불일치)과는 별개 정보다 — 인증이 '적합'이어도 안전하다는 뜻이 아니다(9/30 원칙).
   서버 VerificationResultResponse.kc (KcCertResponse). 인증번호를 안 넣었으면 null 이라 아무것도 안 그린다. */
function kcCertHtml(d) {
  const kc = d && d.kc;
  if (!kc || !kc.status) return '';
  let body;
  let warn = false;
  if (kc.status === 'FOUND') {
    const facts = [kc.certNum, kc.modelName, kc.certDate ? fmtDate(kc.certDate) + ' 인증' : null]
            .filter(Boolean).map(esc).join(' · ');
    const state = esc(kc.certState || '상태 정보 없음');
    warn = !!kc.needsAttention;
    body = (warn ? '⚠ 인증상태 <strong>' + state + '</strong>' : '인증상태 ' + state) + ' — ' + facts;
    body += '<br>인증 모델명이 리콜 공표문 모델명과 같을 때만 판정 근거로 씁니다(아래 판정 근거의 \'인증 모델명\').';
    if (!warn) body += '<br>인증 여부는 리콜·안전 여부와 별개입니다.';
  } else if (kc.status === 'NOT_FOUND') {
    warn = true;
    body = esc(kc.certNum || '') + ' — KC 인증 DB에서 이 번호를 찾지 못했습니다. 번호를 다시 확인해 주세요.';
  } else {
    body = 'KC 인증 DB를 지금 조회하지 못했습니다' + (kc.certNum ? ' (' + esc(kc.certNum) + ')' : '') +
            '. 리콜 대조는 입력한 정보로 그대로 진행했습니다.';
  }
  return '<br><span class="kc-cert" style="display:block;margin-top:8px;font-size:0.92em;' +
          (warn ? 'color:#b45309;' : '') + '">🔖 KC 인증 조회: ' + body + '</span>';
}

/* 9/27 — 사진으로 찾기 버튼과 결과 문구. 서버가 imageCheckAvailable 을 내려줄 때만 버튼을 보인다
   (10/10 — 사진이 있고, 아직 안 눌렀고, 일치가 아니고, KC 번호로 판단할 수 없는 건: 번호 미표기·미입력이거나
   적힌 번호가 KC 인증 DB 에 없을 때). */
const IMAGE_CHECK_NOTE = {
  FOUND: '📷 사진으로 웹을 검색해 공표문과 한 번 더 대조했습니다.',
  NONE: '📷 사진으로 웹을 검색했지만 여러 사이트에서 반복된 상품명·KC 번호·모델명을 찾지 못했습니다.'
};

function imageCheckHtml(d) {
  if (!d) return '';
  if (d.imageCheckAvailable && d.verificationId) {
    return '<br><button type="button" class="image-check-btn" ' +
        'onclick="event.stopPropagation(); checkImage(' + Number(d.verificationId) + ', this)">' +
        '📷 사진으로 찾기</button>';
  }
  /* 10/7 — 사진으로 찾은 상품명(여러 사이트에서 반복된 것)을 보여 준다. 없으면 '특정 못 함'으로 적는다.
     10/7 이전에 사진 확인한 건은 상품명이 저장돼 있지 않아 '특정 못 함'으로 보인다. */
  if (d.imageCheck === 'FOUND') {
    /* 10/10 — 최종 흐름 2차: 같은 사진이 실린 서로 다른 사이트 2곳 이상에서 반복된 상품명·KC 번호·모델명 */
    const facts = [
      d.imageProductName ? '상품명 <b>' + esc(d.imageProductName) + '</b>' : null,
      d.imageCertNum ? 'KC 번호 <b>' + esc(d.imageCertNum) + '</b>' : null,
      d.imageModelName ? '모델명 <b>' + esc(d.imageModelName) + '</b>' : null
    ].filter(Boolean);
    const found = facts.length
            ? '📷 사진으로 찾은 정보: ' + facts.join(' · ') + ' — 같은 사진이 여러 사이트에 실려 있고 그 사이트들에서 ' +
              '같은 값이 반복돼 이 값으로 공표문과 한 번 더 대조했습니다. 판매자가 직접 적은 값이 아니라서 최대 \'의심\'까지만 판정합니다.'
            : '📷 사진으로 웹을 검색했지만 여러 사이트에 반복된 같은 상품을 찾지 못해 상품을 특정하지 못했습니다.';
    return '<span class="image-check-note">' + found + '</span>';
  }
  const note = IMAGE_CHECK_NOTE[d.imageCheck];
  return note ? '<span class="image-check-note">' + note + '</span>' : '';
}

/* 사진으로 찾기 — POST /api/verifications/{id}/image-check. 사용자가 누를 때만 Google Vision 을 쓴다.
   결과 화면에서 누르면 결과를 다시 그리고, 쿠팡 목록에서 누르면 그 건을 결과 화면으로 연다. */
let imageCheckBusy = false;

async function checkImage(id, btn) {
  if (!id || imageCheckBusy) return;
  if (DEMO_MODE) {
    if (btn) btn.textContent = '더미 모드에서는 사진 판독을 하지 않습니다';
    return;
  }
  imageCheckBusy = true;
  const label = btn ? btn.textContent : '';
  if (btn) {
    btn.disabled = true;
    btn.textContent = '사진 판독 중…';
  }
  try {
    const r = await call('/api/verifications/' + id + '/image-check', 'POST');
    if (!r.json || !r.json.success) {
      if (btn) {
        btn.disabled = false;
        btn.textContent = '📷 다시 시도 — ' + ((r.json && r.json.message) || ('오류 ' + r.status));
      }
      return;
    }
    const result = r.json.data;
    renderInput(emptyInput());
    render(result);
    showScreen('verifyScreen');
    const panel = document.getElementById('resultScreen');
    if (panel) {
      panel.classList.add('show');
      panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    const evidence = await loadEvidence(id);
    if (evidence) applyEvidenceInputs(evidence.comparisons);
  } finally {
    imageCheckBusy = false;
    if (btn && btn.isConnected && btn.disabled) {
      btn.disabled = false;
      btn.textContent = label;
    }
  }
}


/* ==================================================
   EVIDENCE
   MatchEvidenceResponse
     (verificationId, recallUid, decision, similarityScore, reason,
      recallProductName, publishDate, comparisons)
   comparisons[] = FieldComparison(field, inputValue, officialValue, score, weight)
================================================== */

async function loadEvidence(id) {

  if (!id) return;

  let data = null;

  if (DEMO_MODE) {
    data = DEMO_EVIDENCE[id] || null;
  } else {
    const r = await call('/api/verifications/' + id + '/evidence', 'GET');
    if (r.json && r.json.success) data = r.json.data;
  }

  const reasonEl = document.getElementById('reason');

  if (!data) {
    if (reasonEl) reasonEl.textContent = '판정 근거를 불러오지 못했습니다.';
    renderComparisons(null);
    return;
  }

  if (reasonEl) {
    reasonEl.textContent = data.reason
        ? '판정 근거 · ' + data.reason
        : '판정 근거가 기록되지 않았습니다.';
  }

  renderComparisons(data.comparisons, data.decision);
  return data;
}


function renderComparisons(comparisons, decision) {

  const box = document.getElementById('evidenceTable');
  if (!box) return;

  if (!comparisons || !comparisons.length) {
    box.innerHTML =
        '<div class="evidence-empty">항목별 대조 결과가 없습니다.</div>';
    return;
  }

  const items = comparisons.filter(function (c) { return c && typeof c === 'object'; });
  const image = items.find(function (c) { return c.field === 'imageLabel'; });
  const textItems = items.filter(function (c) { return c.field !== 'imageLabel'; });
  if (!items.length) {
    box.innerHTML = '<div class="evidence-empty">항목별 대조 결과가 없습니다.</div>';
    return;
  }

  // imageLabel.score는 Vision이 추출한 문구와 공표문 간 텍스트 대조 점수다.
  // 항목별 이미지 점수가 아니므로 공통 셀로 한 번만 표시한다.
  function scoreCell(c) {
    const raw = c && c.score;
    const score = (typeof raw === 'number' || (typeof raw === 'string' && raw.trim() !== ''))
        ? Number(raw) : NaN;
    if (!Number.isFinite(score) || score < 0 || score > 1) return '<span>미제공</span>';
    const unused = c.weight !== null && c.weight !== undefined && Number(c.weight) === 0;
    return '<span class="evidence-bar"><i style="width:' + (score * 100) + '%"></i></span>' +
        '<b>' + (score * 100).toFixed(1) + '%</b>' +
        (unused ? '<span class="evidence-weight">판정 미반영</span>' : '');
  }
  const visibleItems = textItems.length ? textItems : [null];
  const rows = visibleItems.map(function (c, index) {
    const label = c ? (FIELD_LABEL[c.field] || c.field || '항목') : '텍스트 대조 없음';
    return '<tr>' +
        '<td><span class="evidence-field">' + esc(label) + '</span>' + esc(c && c.inputValue != null ? c.inputValue : '-') + '</td>' +
        '<td>' + esc(c && c.officialValue != null ? c.officialValue : '-') + '</td>' +
        '<td class="evidence-score">' + scoreCell(c) + '</td>' +
        (index === 0 ? '<td class="evidence-score evidence-image-score" rowspan="' + visibleItems.length + '">' +
            scoreCell(image) + (image ? '<span class="evidence-weight">Vision 문구 대조 · 공통 점수</span>' +
                '<span class="evidence-image-detail">입력: ' + esc(image.inputValue == null ? '-' : image.inputValue) +
                '<br>공표문: ' + esc(image.officialValue == null ? '-' : image.officialValue) + '</span>' : '') + '</td>' : '') +
        '</tr>';
  }).join('');

  const table =
          '<table class="evidence-table">' +
          '<thead><tr>' +
          '<th>항목</th><th>내 입력값</th><th>공표문 값</th><th>유사도</th>' +
          '</tr></thead>' +
          '<tbody>' + rows + '</tbody></table>';

  /* 10/7 — 불일치 건의 표는 '가장 가까운 후보'와의 대조라 무관한 공표문이 '공표문 값'처럼 보였다.
     접어 두고 열면 참고로만 보이게 한다. 판정·데이터는 그대로다. */
  if (decision === 'NO_MATCH') {
    box.innerHTML =
            '<div class="evidence-empty">비슷한 리콜 공표문이 없습니다.</div>' +
            '<details style="margin-top:8px"><summary style="cursor:pointer;font-size:0.92em">' +
            '가장 가까운 후보와의 대조 보기 (참고 — 의심 기준에 못 미친 공표문)</summary>' +
            table + '</details>';
    return;
  }

  box.innerHTML = table;
}


/* comparisons 의 inputValue 로 입력 비교 박스를 채운다.
   이력에서 과거 건을 열 때 쓴다(그때는 lastInput 이 없다). */
function applyEvidenceInputs(comparisons) {

  if (!comparisons) return;

  const filled = emptyInput();

  comparisons.forEach(function (c) {
    const elId = INPUT_FIELD_EL[c.field];
    if (elId) text(elId, c.inputValue);
    if (c.field in filled) filled[c.field] = c.inputValue || null;
  });

  text('inputBarcode', null); /* 바코드는 대조 항목이 아니라 근거에 안 들어온다 */
  lastInput = filled;
}


/* ==================================================
   HISTORY
   channel 로 분리한다. WEB = 제품 정보로 직접 검증,
   EXTENSION = 크롬 확장이 쿠팡 주문내역에서 배치로 올린 건.
================================================== */

function showHistoryType(type) {

  const manual = type !== 'coupang';

  const manualCard = document.getElementById('manualHistoryCard');
  const coupangCard = document.getElementById('coupangHistoryCard');

  if (manualCard) manualCard.style.display = manual ? 'block' : 'none';
  if (coupangCard) coupangCard.style.display = manual ? 'none' : 'block';

  const tabManual = document.getElementById('historyTabManual');
  const tabCoupang = document.getElementById('historyTabCoupang');

  if (tabManual) tabManual.classList.toggle('active', manual);
  if (tabCoupang) tabCoupang.classList.toggle('active', !manual);

  if (manual) loadHistoryChannel('WEB', 'historyListManual');
  else loadHistoryChannel('EXTENSION', 'historyListCoupang');
}


async function loadHistoryChannel(channel, targetId) {

  if (DEMO_MODE) {
    renderHistoryList(targetId, DEMO_HISTORY[channel] || []);
    return;
  }

  const list = document.getElementById(targetId);
  if (list) {
    list.innerHTML =
        '<div class="history-empty">불러오는 중...</div>';
  }

  const r = await call(
      '/api/verifications/me?page=0&size=20&channel=' + channel,
      'GET'
  );

  if (!r.json || !r.json.success) {
    if (list) {
      list.innerHTML =
          '<div class="history-empty">이력을 불러오지 못했습니다 (' +
          r.status + ').</div>';
    }
    return;
  }

  renderHistoryList(targetId, (r.json.data && r.json.data.content) || []);
}


/* 9/30 — 이력 한 줄에 판매자가 적은 KC 인증정보를 짧게 붙인다(오픈마켓이라 판매자 표기가 제각각이다) */
function kcHint(v) {
  if (!v || !v.kcStatus) return '';
  const t = v.kcText ? String(v.kcText).slice(0, 30) : '';
  switch (v.kcStatus) {
    case 'DISCLOSED': return 'KC 번호 있음';
    case 'REFERENCED':
      /* 9/30 — 확장이 쿠팡 카테고리로 어린이제품이라 보고 올린 건(판매자는 '해당없음'·KC 칸 없음) */
      if (/카테고리상 어린이제품/.test(v.kcText || '')) return 'KC 번호 없음 (어린이제품 카테고리)';
      return 'KC 번호 없음' + (t ? ' (' + t + ')' : '');
    case 'UNREADABLE': return 'KC 정보 못 읽음';
    case 'NONE': return t ? 'KC: ' + t : 'KC 표기 없음';
    default: return '';
  }
}

/* 10/3 — 이력 한 줄에 KC 인증 DB 조회 결과를 짧게 붙인다(KC인증 연동) */
function kcDbHint(v) {
  if (!v || !v.kcLookup) return '';
  if (v.kcLookup === 'FOUND') return 'KC 인증 ' + (v.kcCertState || '확인');
  if (v.kcLookup === 'NOT_FOUND') return 'KC 인증 DB에 없는 번호';
  return '';
}

function renderHistoryList(targetId, items) {

  const list = document.getElementById(targetId);
  if (!list) return;

  if (!items || !items.length) {
    list.innerHTML =
        '<div class="history-empty">아직 검증 이력이 없습니다.</div>';
    return;
  }

  list.innerHTML = items.map(function (v) {

    const fr = stateOf(v);
    const icon = v.channel === 'EXTENSION' ? '🛒' : '🧸';

    const sub = [
      fmtDateTime(v.createdAt),
      v.makerName || '',
      kcHint(v),
      kcDbHint(v)
    ].filter(Boolean).join(' · ');

    const statusNote =
        (v.status && v.status !== STATUS_OK)
            ? '<span class="history-status">' +
            esc(STATUS_LABEL[v.status] || v.status) + '</span>'
            : '';

    return '' +
        '<div class="history-item" onclick="openHistoryItem(' +
        Number(v.verificationId) + ')">' +
        '<div class="history-product">' + icon + '</div>' +
        '<div class="history-main">' +
        '<div class="history-name">' +
        esc(v.inputSummary || '제품 정보') + statusNote +
        '</div>' +
        '<div class="history-date">' + esc(sub) + '</div>' +
        '</div>' +
        (v.imageCheckAvailable
            ? '<button type="button" class="image-check-btn" ' +
            'onclick="event.stopPropagation(); checkImage(' + Number(v.verificationId) + ', this)">' +
            '📷 사진으로 찾기</button>'
            : '') +
        '<div class="status-pill status-' + fr + '">' +
        (LABEL[fr] || '-') + '</div>' +
        '</div>';
  }).join('');
}


/* 이력 항목 클릭 → 그 건의 결과 + 근거를 결과 화면에 다시 띄운다. */
async function openHistoryItem(id) {

  if (!id) return;

  let result = null;

  if (DEMO_MODE) {
    const all = DEMO_HISTORY.WEB.concat(DEMO_HISTORY.EXTENSION);
    result = all.find(x => String(x.verificationId) === String(id)) || null;
  } else {
    const r = await call('/api/verifications/' + id, 'GET');
    if (r.json && r.json.success) result = r.json.data;
  }

  if (!result) return;

  renderInput(emptyInput());
  render(result);

  /* 결과 패널(#resultScreen)은 독립 화면이 아니라 verifyScreen 안에 박힌 .embedded-result 다.
     9/24 1차 버전은 showScreen('resultScreen') 을 불러서 verifyScreen 까지 꺼 버렸고,
     이력에서 항목을 누르면 빈 화면이 떴다. 부모 화면을 켠 뒤 패널을 연다. */
  showScreen('verifyScreen');

  const panel = document.getElementById('resultScreen');
  if (panel) {
    panel.classList.add('show');
    panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  const evidence = await loadEvidence(id);
  if (evidence) applyEvidenceInputs(evidence.comparisons);
}


/* ==================================================
   COUPANG — 구매 제품 중 리콜 의심 제품 찾기 (finder)
   크롬 확장은 background.js 에서 POST /api/verifications/manual/batch 로 백엔드에 직접 저장한다.
   이 화면은 EXTENSION 채널 이력을 읽어

     (1) 구매 상품 N개 중 리콜 의심 K개 — 요약 한 줄
     (2) 찾은 의심 상품 목록 (일치 먼저, 그다음 확인 필요)
     (3) 최근 확인한 구매 상품 5건

   을 보여 준다. 9/27 — "리콜 상품이 없다"가 아니라 "구매한 것 중 리콜 상품을 찾아낸다"로 보여 주려고
   요약을 '최신 1건'에서 '찾은 건수'로 바꿨다. 못 찾았을 때도 '안전'이라고 쓰지 않는다.
================================================== */

const COUPANG_RECENT_SIZE = 5;
/* 요약 계산용으로 한 번에 읽는 이력 수. 데모 규모(주문목록 몇 페이지)면 충분하다. */
const COUPANG_SCAN_SIZE = 100;
const COUPANG_SUSPECT_SHOW = 10;
const COUPANG_POLL_MS = 8000;
let coupangPollTimer = null;
let coupangLoading = false;

/* 쿠팡 화면을 보고 있는 동안만 주기적으로 다시 읽는다.
   데모에서 쿠팡 탭을 연 뒤 이 탭으로 돌아오면 방금 들어온 결과가 바로 보이게 하려는 것.
   탭이 백그라운드면 건너뛴다 — 불필요한 호출을 만들지 않는다. */
function startCoupangPolling() {
  stopCoupangPolling();
  loadCoupangRecent();
  coupangPollTimer = setInterval(function () {
    if (document.visibilityState === 'visible') loadCoupangRecent();
  }, COUPANG_POLL_MS);
}

function stopCoupangPolling() {
  if (coupangPollTimer) {
    clearInterval(coupangPollTimer);
    coupangPollTimer = null;
  }
}

function refreshCoupang() {
  paintExtensionStatus();
  loadCoupangRecent();
}


/* ==================================================
   10/12 — 쿠팡 구매이력 읽기 버튼 (멘토링 피드백: 다 읽어서 오래 걸린다)
   a. 최근 N건(10/30) 읽기   b. 구매일자(하루) 읽기
   이 화면 → (window 이벤트) → 확장 auth-bridge → background 가 쿠팡 주문목록을 보이지 않는 탭으로 열어
   고른 주문만 상세페이지를 읽고 서버로 보낸다. 진행 상황은 underfaker-scan-status 이벤트로 돌아온다.
   이벤트 내용은 JSON 문자열로 주고받는다(확장 쪽 세계와 객체를 바로 나누지 못한다).
================================================== */

var coupangScanFilter = null;   // { ids: Set<verificationId>, label } — 이번 조회 결과만 보기
var coupangScanBusy = false;
var coupangScanWatchdog = null;
var coupangScanReq = null;      // 이 화면에서 보낸 요청 { mode, limit, date } — 상태 문구에 쓴다
var coupangScanId = null;       // 확장이 정한 이번 읽기 번호 — 다른 읽기의 늦은 소식을 걸러낸다

const SCAN_ERROR_TEXT = {
  BUSY: '이미 읽는 중입니다. 끝난 뒤 다시 눌러 주세요.',
  LOGIN_REQUIRED: '확장 프로그램이 UnderFaker 로그인 정보를 아직 받지 못했습니다. 로그아웃 후 다시 로그인해 주세요.',
  BAD_LIMIT: '읽을 건수는 10건 또는 30건만 고를 수 있습니다.',
  BAD_DATE: '구매일자를 다시 골라 주세요.',
  NO_PAGE: '쿠팡 주문목록을 열지 못했습니다. 이 브라우저에서 쿠팡에 로그인돼 있는지 확인해 주세요.',
  DATE_UNREADABLE: '쿠팡 주문목록에서 주문일을 읽지 못했습니다. 쿠팡 화면 구조가 달라졌을 수 있습니다 — 최근 N건 읽기를 써 주세요.',
  TAB_FAILED: '쿠팡 주문목록 탭을 열지 못했습니다.',
  CONTEXT_LOST: '확장 프로그램이 새로고침돼 이 페이지와 연결이 끊겼습니다. 이 페이지를 새로고침(F5)해 주세요.',
  NO_EXTENSION: '확장 프로그램이 응답하지 않습니다. 확장 0.5.0 이상을 설치하고 이 페이지를 새로고침해 주세요.',
  FAILED: '읽는 도중 오류가 났습니다. 들어온 결과까지만 아래에 보입니다. 다시 눌러 주세요.',
  STALLED: '확장 프로그램의 진행 소식이 1분 넘게 없습니다. 아래 목록에 들어온 결과를 확인하고, 필요하면 다시 눌러 주세요.'
};

/* 진행 소식이 이 시간 넘게 없으면 멈춘 것으로 보고 버튼을 다시 켠다(확장 service worker 가 중간에 꺼진 경우 등).
   상세페이지 한 건은 최대 25초라 그보다 넉넉히 둔다. */
const SCAN_STALL_MS = 60000;

function armScanWatchdog(ms, errorKey) {
  clearTimeout(coupangScanWatchdog);
  coupangScanWatchdog = setTimeout(function () {
    if (coupangScanBusy) {
      finishScanUi(SCAN_ERROR_TEXT[errorKey], 'err');
      loadCoupangRecent();
    }
  }, ms);
}

function todayYmd() {
  const d = new Date();
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

function ymdKorean(ymd) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd || '');
  return m ? Number(m[1]) % 100 + '년 ' + Number(m[2]) + '월 ' + Number(m[3]) + '일' : ymd;
}

function paintScanControls() {
  const supported = extensionSupports('scan');
  ['scanRecentBtn', 'scanDateBtn'].forEach(function (id) {
    const b = document.getElementById(id);
    if (b) b.disabled = coupangScanBusy || !supported || DEMO_MODE;
  });
  const dateEl = document.getElementById('scanDate');
  if (dateEl) {
    dateEl.max = todayYmd();
    if (!dateEl.value) dateEl.value = todayYmd();
  }
  if (!supported && !coupangScanBusy) {
    setScanStatus(DEMO_MODE ? '더미 모드에서는 쿠팡을 읽지 않습니다.'
        : '확장 프로그램 0.5.0 이상이 있어야 읽기 버튼을 쓸 수 있습니다.', 'idle');
  }
}

function setScanStatus(textValue, kind) {
  const el = document.getElementById('scanStatus');
  if (!el) return;
  el.textContent = textValue || '';
  el.className = 'scan-status ' + (kind || 'idle');
}

function scanLabel(mode, limit, date) {
  return mode === 'recent' ? '최근 ' + limit + '건' : ymdKorean(date) + ' 구매';
}

function startCoupangScan(mode) {
  if (coupangScanBusy) return;
  if (!token) {
    updateLoginMessages();
    setScanStatus('먼저 로그인해 주세요.', 'err');
    return;
  }
  if (!extensionSupports('scan')) {
    setScanStatus(SCAN_ERROR_TEXT.NO_EXTENSION, 'err');
    return;
  }
  const req = { mode: mode };
  if (mode === 'recent') {
    req.limit = Number((document.getElementById('scanLimit') || {}).value || 10);
  } else {
    req.date = (document.getElementById('scanDate') || {}).value || '';
    if (!/^\d{4}-\d{2}-\d{2}$/.test(req.date)) {
      setScanStatus(SCAN_ERROR_TEXT.BAD_DATE, 'err');
      return;
    }
    if (req.date > todayYmd()) {
      setScanStatus('오늘 이후 날짜는 고를 수 없습니다.', 'err');
      return;
    }
  }
  coupangScanBusy = true;
  coupangScanFilter = null;
  coupangScanReq = req;
  coupangScanId = null;
  paintScanControls();
  setScanStatus(scanLabel(mode, req.limit, req.date) + ' 읽기를 요청했습니다…', 'busy');
  /* 확장이 10초 안에 받았다는 답(started/error)을 주지 않으면 연결 문제로 본다 */
  armScanWatchdog(10000, 'NO_EXTENSION');
  window.dispatchEvent(new CustomEvent('underfaker-scan-start', { detail: JSON.stringify(req) }));
}

function finishScanUi(message, kind) {
  clearTimeout(coupangScanWatchdog);
  coupangScanBusy = false;
  paintScanControls();
  setScanStatus(message, kind);
}

function onScanStatus(s) {
  if (!s || !s.phase) return;
  if (!coupangScanBusy) return; // 이 화면에서 시작한 읽기가 아니다(끝난 뒤 늦게 온 소식 포함)
  /* 이번 읽기 번호를 처음 받은 값으로 정하고, 다른 번호의 소식은 버린다 */
  if (s.scanId) {
    if (coupangScanId === null) coupangScanId = s.scanId;
    else if (s.scanId !== coupangScanId) return;
  }
  const req = coupangScanReq || {};
  const label = scanLabel(s.mode || req.mode, s.limit || req.limit, s.date || req.date);
  if (s.phase === 'listing' || s.phase === 'reading' || s.phase === 'started') armScanWatchdog(SCAN_STALL_MS, 'STALLED');
  if (s.phase === 'started') {
    /* 쿠팡 주문목록 한 쪽이 20초 넘게 안 오면 확장이 NO_PAGE 를 보낸다.
       확장이 'listing' 을 먼저 보냈으면 그 문구를 덮지 않는다 */
    const cur = document.getElementById('scanStatus');
    if (!cur || !/쪽 읽는 중|읽는 중 \d/.test(cur.textContent)) setScanStatus(label + ' — 쿠팡 주문목록을 여는 중…', 'busy');
  } else if (s.phase === 'listing') {
    setScanStatus(label + ' — 주문목록 ' + (s.page || 1) + '쪽 읽는 중' +
        (s.found ? ' (고른 상품 ' + s.found + '건)' : '') + '…', 'busy');
  } else if (s.phase === 'reading') {
    setScanStatus(label + ' — 고른 상품 ' + (s.total || 0) + '건의 상세페이지(KC 인증정보) 읽는 중 ' +
        (s.done || 0) + '/' + (s.total || 0) + '…', 'busy');
    loadCoupangRecent();
  } else if (s.phase === 'done') {
    const ids = new Set(s.verificationIds || []);
    coupangScanFilter = { ids: ids, label: label };
    const secs = s.elapsedMs ? Math.round(s.elapsedMs / 1000) : null;
    const avg = (s.avgReadMs !== null && s.avgReadMs !== undefined)
        ? ' · 상세페이지 한 건당 평균 ' + (s.avgReadMs / 1000).toFixed(1) + '초' : '';
    finishScanUi(s.found
        ? label + ' — ' + s.found + '건을 읽어 ' + (s.sent || 0) + '건 확인했습니다' +
          (secs !== null ? ' (' + secs + '초' + avg + ')' : '') + '.'
        : label + ' — 읽을 주문이 없습니다' + (s.mode === 'date' ? ' (그날 주문이 없거나 주문목록 앞쪽 ' + (s.pagesRead || 0) + '쪽 안에 없음)' : '') + '.',
        s.found ? 'ok' : 'idle');
    loadCoupangRecent();
  } else if (s.phase === 'error') {
    finishScanUi(label + ' — ' + (SCAN_ERROR_TEXT[s.error] || ('읽지 못했습니다 (' + s.error + ')')), 'err');
    if (s.verificationIds && s.verificationIds.length) {
      coupangScanFilter = { ids: new Set(s.verificationIds), label: label + ' (중간까지)' };
    }
    loadCoupangRecent();
  }
}

window.addEventListener('underfaker-scan-status', function (e) {
  let s = null;
  try { s = typeof e.detail === 'string' ? JSON.parse(e.detail) : null; } catch (err) { s = null; }
  onScanStatus(s);
});

function paintScanFilterBar() {
  const bar = document.getElementById('coupangScanFilterBar');
  if (!bar) return;
  if (!coupangScanFilter) {
    bar.style.display = 'none';
    return;
  }
  bar.style.display = '';
  text('coupangScanFilterLabel', '이번 조회 결과만 보는 중: ' + coupangScanFilter.label +
      ' (' + coupangScanFilter.ids.size + '건)');
}

function clearCoupangScanFilter() {
  coupangScanFilter = null;
  loadCoupangRecent();
}

async function loadCoupangRecent() {

  if (coupangLoading) return;
  coupangLoading = true;

  const btn = document.getElementById('coupangRefreshBtn');
  if (btn) btn.disabled = true;

  try {
    let items = [];

    if (DEMO_MODE) {
      items = DEMO_HISTORY.EXTENSION.slice(0, COUPANG_SCAN_SIZE);
    } else {
      const r = await call(
          '/api/verifications/me?page=0&size=' + COUPANG_SCAN_SIZE + '&channel=EXTENSION',
          'GET'
      );
      if (!r.json || !r.json.success) {
        const list = document.getElementById('coupangRecentList');
        if (list) {
          list.innerHTML = '<div class="history-empty">최근 결과를 불러오지 못했습니다 (' + r.status + ').</div>';
        }
        return;
      }
      items = (r.json.data && r.json.data.content) || [];
    }

    /* 10/12 — 방금 버튼으로 읽은 결과만 보여 준다(확장이 돌려준 검증 id). '전체 보기'로 푼다. */
    if (coupangScanFilter) {
      items = items.filter(function (v) { return coupangScanFilter.ids.has(v.verificationId); });
    }
    paintScanFilterBar();

    const finder = summarizeCoupang(items);
    updateCoupangResultCard(finder);
    renderHistoryList('coupangSuspectList', finder.suspects.slice(0, COUPANG_SUSPECT_SHOW));
    const suspectList = document.getElementById('coupangSuspectList');
    if (suspectList && !finder.suspects.length) suspectList.innerHTML = '';
    /* 9/27 — KC 번호 미표기(구 항목누락) 목록(사진으로 찾기 버튼) */
    renderHistoryList('coupangMissingList', finder.missingItems.slice(0, COUPANG_SUSPECT_SHOW));
    const missingList = document.getElementById('coupangMissingList');
    if (missingList && !finder.missingItems.length) missingList.innerHTML = '';
    const missingTitle = document.getElementById('coupangMissingTitle');
    if (missingTitle) missingTitle.style.display = finder.missingItems.length ? '' : 'none';
    /* 최근 목록도 같은 상품은 최신 1건만 — 같은 이름이 연달아 보이지 않게 */
    renderHistoryList('coupangRecentList', finder.unique.slice(0, COUPANG_RECENT_SIZE));

  } finally {
    coupangLoading = false;
    if (btn) btn.disabled = false;
  }
}

/* 9/24 1차 버전과의 호환용 — 테스트·외부 호출이 남아 있을 수 있다. */
async function loadLatestCoupangResult() {
  return loadCoupangRecent();
}

/* 구매이력 요약.
   같은 상품이 여러 번 들어올 수 있다(주문목록을 다시 열면 확장이 다시 보낸다).
   상품명 기준으로 최신 1건만 센다. 이력은 최신순으로 오므로 처음 본 것이 최신이다.
   의심 = 처리가 끝난(DONE) 건 중 일치·부분일치. 처리 실패·처리 중은 따로 센다. */
/* 9/27 (2차) — 같은 상품인지 가르는 키.
   실측(v0.4.0 화면): 항목누락 목록에 '소바바 소이허니 순살 (냉동), 600g, 2개' ·
   '백설 허브맛 솔트 오리지널, 50g, 1개' · '신지모루 트리플 3in1 …' 가 두 번씩 보였다.
   화면에 보이는 글자는 같았는데 공백만 합치던 예전 키로는 걸러지지 않았다 — 보이지 않는 글자
   (폭 없는 공백·전각 문자 등) 차이로 추정한다(확실하지 않음, 원문 바이트는 확인 못 함).
   그래서 키를 이렇게 만든다: NFKC 정규화 → 소문자 → 폭 없는 글자 제거 → 끝의 구매 수량
   (", 1개" "2팩" "12개입") 제거 → 한글·영문·숫자 외 전부 제거.
   수량만 다른 같은 상품('600g, 1개' / '600g, 2개')도 한 줄로 센다 — 리콜은 상품 단위로 나온다. */
const QTY_TAIL = /[\s,·x×*]*\d+\s*(?:개입|개|팩|입|매|롤|박스|세트|set|ea|pcs)\s*$/i;

function productKey(v) {
  const id = '#' + (v ? v.verificationId : '');
  if (!v || !v.inputSummary) return id;
  let s = String(v.inputSummary);
  try { s = s.normalize('NFKC'); } catch (e) { /* 오래된 브라우저 */ }
  s = s.toLowerCase().replace(/[\u200b-\u200d\u2060\ufeff\u00ad]/g, '');
  for (let i = 0; i < 3 && QTY_TAIL.test(s); i++) s = s.replace(QTY_TAIL, '');
  s = s.replace(/[^0-9a-z\uac00-\ud7a3]/g, '');
  return s || id;
}

function summarizeCoupang(items) {
  const seen = new Set();
  const unique = [];
  (items || []).forEach(function (v) {
    const key = productKey(v);
    if (seen.has(key)) return;
    seen.add(key);
    unique.push(v);
  });

  const done = unique.filter(v => !v.status || v.status === STATUS_OK);
  const rank = { MATCH: 0, PARTIAL: 1 };
  const suspects = done
      .filter(v => stateOf(v) === 'MATCH' || stateOf(v) === 'PARTIAL')
      .sort((a, b) => rank[stateOf(a)] - rank[stateOf(b)]);
  /* 9/27 — KC 번호 미표기(구 항목누락): 텍스트로 못 찾았고 KC 인증번호도 없는 건. 사진으로 찾기 버튼 대상 */
  const missing = done.filter(v => stateOf(v) === 'MISSING');
  /* 10/10 — 판단 제외: 해외직구·구매대행 상품(서버 ResultView.EXCLUDED) */
  const excluded = done.filter(v => stateOf(v) === 'EXCLUDED');
  /* 9/30 — 서버 이력 응답의 kcStatus(확장이 쿠팡 고시에서 읽은 KC 인증정보 상태).
     KC 대상 = 번호 있음·번호 없음(참조)·못 읽음. NONE = KC 칸이 없거나 '해당없음'. 없으면(구 기록·웹) 모름 */
  const KC_TARGET = { DISCLOSED: 1, REFERENCED: 1, UNREADABLE: 1 };
  /* 10/10 — 판단 제외(해외직구·구매대행) 건은 대조하지 않았으므로 KC 대상 집계에서 뺀다 */
  const kcKnown = done.filter(v => !!v.kcStatus && stateOf(v) !== 'EXCLUDED');
  const kcTarget = kcKnown.filter(v => KC_TARGET[v.kcStatus]).length;
  const kcNone = kcKnown.filter(v => v.kcStatus === 'NONE').length;

  return {
    unique: unique,
    total: unique.length,
    done: done.length,
    suspects: suspects,
    missingItems: missing,
    match: suspects.filter(v => stateOf(v) === 'MATCH').length,
    partial: suspects.filter(v => stateOf(v) === 'PARTIAL').length,
    missing: missing.length,
    excluded: excluded.length,
    /* 10/7 — KC 번호 미표기 중 아직 사진으로 찾기를 누를 수 있는 건(사진 확인을 한 건도 그대로 남는다) */
    missingCheckable: missing.filter(v => v.imageCheckAvailable).length,
    kcKnown: kcKnown.length,
    kcTarget: kcTarget,
    kcNone: kcNone,
    pending: unique.filter(v => v.status === 'PENDING').length,
    failed: unique.filter(v => v.status === 'FAILED').length
  };
}


function updateCoupangResultCard(f) {

  const card = document.getElementById('coupangResultCard');
  if (!card) return;

  card.classList.add('show');

  /* 클릭 대상은 요약 줄만. 카드 전체에 걸면 아래 목록 항목·새로고침 버튼 클릭이
     버블링돼서 늘 같은 건이 열린다. */
  card.onclick = null;
  card.style.cursor = '';
  const summary = card.querySelector('.coupang-result-summary');
  if (summary) {
    summary.onclick = null;
    summary.style.cursor = '';
  }

  const pill = document.getElementById('coupangResultStatus');
  const meta = document.getElementById('coupangResultMeta');

  if (!f || !f.total) {
    text('coupangResultProduct', '아직 쿠팡에서 넘어온 검증이 없습니다');
    /* 9/30 — 주문목록을 못 읽었을 때의 대안(직접 입력)을 같이 안내한다 */
    if (meta) {
      meta.textContent = '쿠팡 주문목록 페이지를 열면 확장 프로그램이 자동으로 보냅니다. ' +
          '열었는데도 넘어오지 않으면 확장 프로그램 설치·로그인을 확인하고, 그래도 안 되면 ' +
          "위 '품명으로 제품 확인'에서 제품명을 직접 입력해 확인하세요.";
    }
    if (pill) pill.style.display = 'none';
    return;
  }

  const extra = [];
  if (f.missing) {
    extra.push('KC 번호 미표기 ' + f.missing + '개' +
            (f.missingCheckable ? ' (사진으로 찾기 가능 ' + f.missingCheckable + '개)' : ''));
  }
  if (f.excluded) extra.push('(어린이제품이 아닌) 해외직구·구매대행 판단 제외 ' + f.excluded + '개');
  if (f.pending) extra.push('확인 중 ' + f.pending + '개');
  if (f.failed) extra.push('처리 실패 ' + f.failed + '개');

  if (pill) pill.style.display = '';

  if (f.suspects.length) {
    text('coupangResultProduct',
        '구매한 제품 ' + f.done + '개 중 리콜 의심 제품 ' + f.suspects.length + '개를 찾았습니다');
    if (meta) {
      meta.textContent = ['리콜 일치 ' + f.match + '개', '의심 ' + f.partial + '개']
          .concat(extra).join(' · ') + ' — 제품을 눌러 판정 근거를 확인하세요.';
    }
    if (pill) {
      pill.className = 'status-pill status-' + (f.match ? 'MATCH' : 'PARTIAL');
      pill.textContent = '의심 ' + f.suspects.length + '개';
    }
    const first = f.suspects[0];
    if (first && first.verificationId && summary) {
      summary.onclick = function () { openHistoryItem(first.verificationId); };
      summary.style.cursor = 'pointer';
    }
    return;
  }

  if (!f.done) {
    text('coupangResultProduct', '구매 상품 ' + f.total + '개를 확인하고 있습니다');
    if (meta) meta.textContent = extra.join(' · ');
  } else if (f.missing) {
    text('coupangResultProduct',
            '구매한 제품 ' + f.done + '개 중 KC 번호 미표기 ' + f.missing + '개' +
            (f.missingCheckable ? ' — 사진으로 찾아보세요' : ' — KC 인증번호를 확인하지 못했습니다'));
    if (meta) {
      meta.textContent = f.missingCheckable
              ? '상품명만으로는 리콜 의심 제품을 찾지 못했습니다. KC 인증번호가 없는 제품은 ' +
                '아래에서 "사진으로 찾기"를 누르면 사진으로 웹을 검색해 한 번 더 대조합니다.'
              : '사진으로도 찾아봤지만 리콜 의심 제품은 없었습니다. 아래 제품은 KC 인증번호가 표기되지 않아 ' +
                '인증 여부를 확인하지 못했습니다.';
    }
    if (pill) {
      pill.className = 'status-pill status-MISSING';
      pill.textContent = 'KC 번호 미표기 ' + f.missing + '개';
    }
    return;
  } else if (f.kcKnown && !f.kcTarget) {
    /* 9/30 — KC 인증 대상으로 표기된 제품이 하나도 없을 때. '찾지 못함'만 보이면 고장인지 정상인지 구분이 안 된다. */
    text('coupangResultProduct',
        '구매한 제품 ' + f.done + '개 중 KC 인증 대상으로 표기된 제품이 없습니다');
    if (meta) {
      meta.textContent = extra.concat([
        /* 9/30 — 근거: 제4차 어린이제품 안전관리 기본계획(2025.1, 국가기술표준원) — 어린이제품안전법은
           13세 이하 어린이제품 대상이며 약사법·식품위생법·화장품법 등 타법 소관 품목은 제외 */
        '판매자 표기(필수 표기 정보)상 KC 인증 대상이 아닌 제품입니다(KC 칸 없음·"해당없음"·식품·화장품·의약외품 고시).',
        '식품·화장품·의약외품은 식품위생법·화장품법·약사법 소관이라 국가기술표준원 리콜 대상이 아닙니다.',
        '찾지 못했다고 안전하다는 뜻은 아닙니다.'
      ]).join(' ');
    }
    if (pill) {
      pill.className = 'status-pill status-UNKNOWN';
      pill.textContent = 'KC 대상 0개';
    }
    return;
  } else {
    text('coupangResultProduct',
        '구매한 제품 ' + f.done + '개에서 리콜 의심 제품을 찾지 못했습니다');
    if (meta) {
      const kcNote = f.kcKnown
          ? ['KC 인증 대상 ' + f.kcTarget + '개 · 대상 아님 ' + f.kcNone + '개를 대조했습니다.']
          : [];
      meta.textContent = extra.concat(kcNote).concat([
        '찾지 못했다고 안전하다는 뜻은 아닙니다. 공표문과 상품명이 크게 다르면 찾지 못할 수 있습니다.'
      ]).join(' · ');
    }
  }
  if (pill) {
    pill.className = 'status-pill status-UNKNOWN';
    pill.textContent = '찾지 못함';
  }
}


/* ==================================================
   DEMO DATA
   백엔드와 동일한 필드명으로 만든다. 더미와 실서버가 같은 모양이어야
   P0-1 같은 필드명 불일치가 더미 단계에서 드러난다.
================================================== */

let demoSeq = 1;

function createDemoManualResult(input) {

  const lower = String(input.productName || '').toLowerCase();

  const isMatch =
      lower.includes('물티슈') ||
      lower.includes('장난감') ||
      lower.includes('슬라임') ||
      lower.includes('리콜');

  const id = demoSeq++;

  const result = {
    verificationId: id,
    finalResult: isMatch ? 'MATCH' : 'NO_MATCH',
    similarityScore: isMatch ? 0.968 : 0.312,
    recallUid: isMatch ? 10023005 : null,

    recallProductName: isMatch ? (input.productName + ' (더미 공표문)') : null,
    recallBrandName: isMatch ? (input.brandName || '더미브랜드') : null,
    recallModelName: isMatch ? (input.modelName || 'CB065R2649-4001') : null,
    recallTypeName: isMatch ? '자발적리콜' : null,
    recallMeans: isMatch ? '교환 또는 환급' : null,
    recallCmpnyName: isMatch ? (input.makerName || '더미유통') : null,
    makerName: isMatch ? (input.makerName || '더미제조') : null,

    harmDscr: isMatch ? '프탈레이트계 가소제 기준 초과(더미)' : null,
    accidentCaseDscr: isMatch ? '피부 접촉 시 위해 우려(더미)' : null,
    publishActionDscr: isMatch
        ? '즉시 사용을 중지하고 구입처에서 교환 또는 환급받으십시오(더미).'
        : null,

    publishDate: isMatch ? '20260901' : null,
    createdAt: new Date().toISOString().substring(0, 19)
  };

  DEMO_HISTORY.WEB.unshift({
    verificationId: id,
    inputType: 'MANUAL',
    channel: 'WEB',
    inputSummary: input.productName,
    makerName: input.makerName || null,
    status: 'DONE',
    finalResult: result.finalResult,
    createdAt: result.createdAt,
    /* 결과 화면 재렌더용으로 응답 전체를 같이 들고 있는다(더미 전용) */
    ...result
  });

  DEMO_HISTORY.WEB = DEMO_HISTORY.WEB.slice(0, 20);

  DEMO_EVIDENCE[id] = {
    verificationId: id,
    recallUid: result.recallUid,
    decision: result.finalResult,
    similarityScore: result.similarityScore,
    reason: isMatch
        ? '모델명 완전 일치, 제품명 부분 일치 (더미)'
        : '일치하는 리콜 공표문을 찾지 못함 (더미)',
    recallProductName: result.recallProductName,
    publishDate: result.publishDate,
    comparisons: isMatch ? [
      { field: 'modelName', inputValue: input.modelName, officialValue: result.recallModelName, score: 1.0, weight: 0.50 },
      { field: 'certNum', inputValue: input.certNum, officialValue: 'CB065R2649-4001', score: 0.82, weight: 0.25 },
      { field: 'productName', inputValue: input.productName, officialValue: result.recallProductName, score: 0.74, weight: 0.10 },
      { field: 'makerName', inputValue: input.makerName, officialValue: result.makerName, score: 0.61, weight: 0.10 },
      { field: 'brandName', inputValue: input.brandName, officialValue: result.recallBrandName, score: 0.55, weight: 0.05 }
    ].concat(input.thumbnailUrl ? [
      /* 이미지 주소를 넣었을 때만 판독 행이 생긴다 — 실제 서버도 썸네일이 없으면 Vision 을 부르지 않는다 */
      { field: 'imageLabel', inputValue: '슬라임 점토 완구', officialValue: result.recallModelName, score: 0.43, weight: 0.05 }
    ] : []) : [
      { field: 'productName', inputValue: input.productName, officialValue: null, score: 0.18, weight: 0.45 },
      { field: 'modelName', inputValue: input.modelName, officialValue: null, score: 0.0, weight: 0.05 }
    ]
  };

  return result;
}


/* ==================================================
   ESCAPE
================================================== */

function esc(s) {
  if (s === null || s === undefined) return '';
  return String(s).replace(/[&<>"']/g, function (c) {
    return {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }[c];
  });
}
