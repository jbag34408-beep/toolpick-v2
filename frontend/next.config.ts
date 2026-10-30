import type { NextConfig } from "next";

// [문법] import type: 타입 정보만 가져온다. 컴파일 후 실행 코드에는 남지 않는다(번들 크기 0)
// [문법] ?? (null 병합 연산자): 왼쪽이 null/undefined일 때만 오른쪽 값. ||와 달리 빈 문자열·0은 그대로 둔다
const backend = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // 브라우저 입장에서 같은 도메인이 되도록 API·로그인 경로를 백엔드로 넘긴다 (세션 쿠키 유지)
  // [CS] rewrite(리버스 프록시): 브라우저는 vercel.app/api/... 로 요청하고, Next 서버가 대신 백엔드에 전달해 응답을 돌려준다.
  // 브라우저는 백엔드 주소를 모르고 같은 출처(origin)로만 통신하므로 CORS 문제가 없고, 백엔드가 준 쿠키가 프론트 도메인에 저장된다.
  // redirect(주소창이 바뀜)와 달리 rewrite는 주소창이 그대로다.
  // [문법] :path* — 경로의 나머지 전부(0개 이상 구간)를 받아 목적지에 그대로 붙인다
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      { source: "/oauth2/:path*", destination: `${backend}/oauth2/:path*` },
      { source: "/login/:path*", destination: `${backend}/login/:path*` },
    ];
  },
  // Turbopack(Next 16 기본 번들러)이 CSS 파일을 Tailwind로 처리하게 한다 (create-next-app 기본 설정)
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
