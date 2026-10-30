/**
 * 카테고리별 대표 이미지와 한 줄 소개. 홈 아코디언과 카테고리 화면 배너에 쓴다.
 * 이미지는 Unsplash(무료 사용) 주소이고, 백엔드에 없는 화면 장식이라 프론트에 둔다.
 */
const unsplash = (id: string) => `https://images.unsplash.com/photo-${id}?w=1200&q=70&auto=format&fit=crop`;

const VISUALS: Record<string, { image: string; tagline: string }> = {
  "ai-agent": { image: unsplash("1677442136019-21780ecad995"), tagline: "코드를 대신 짜고 일을 처리하는 AI 도구" },
  "dev-tools": { image: unsplash("1518432031352-d6fc5c10da5a"), tagline: "터미널과 에디터를 더 빠르게" },
  frontend: { image: unsplash("1547658719-da2b51169166"), tagline: "화면을 만드는 라이브러리와 프레임워크" },
  backend: { image: unsplash("1555066931-4365d14bab8c"), tagline: "서버·API·데이터베이스 연결" },
  data: { image: unsplash("1551288049-bebda4e38f71"), tagline: "분석·시각화·데이터 처리" },
  devops: { image: unsplash("1558494949-ef010cbdcc31"), tagline: "배포·인프라·모니터링 자동화" },
  security: { image: unsplash("1526374965328-7f61d4dc18c5"), tagline: "취약점 점검과 비밀 관리" },
  productivity: { image: unsplash("1484480974693-6ca0a78fb36b"), tagline: "일과 메모를 정리하는 도구" },
};

// 새 카테고리가 생겨도 화면이 깨지지 않게 기본 이미지를 둔다
const FALLBACK = { image: unsplash("1451187580459-43490279c0fa"), tagline: "요즘 뜨는 깃허브 도구" };

export const categoryVisual = (slug: string) => VISUALS[slug] ?? FALLBACK;
