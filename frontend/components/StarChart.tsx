"use client";

import { useEffect, useState } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { getStars, type StarPoint } from "@/lib/api";

/**
 * 최근 30일 스타 추이.
 *
 * [CS] 차트는 브라우저 화면 크기를 재고 마우스 툴팁에 반응해야 해서 클라이언트 컴포넌트다.
 * 상세 페이지 본문(요약·설치 명령)은 서버에서 먼저 그려 빨리 보여주고, 차트 데이터는 브라우저가 따로 받아 나중에 채운다
 * (점진적 로딩). 그동안 같은 크기의 회색 상자(스켈레톤)를 보여 준다.
 *
 * [문법] Recharts는 차트를 컴포넌트 조합으로 만든다: LineChart(틀) 안에 축(XAxis, YAxis), 격자, 툴팁, 선(Line)을 넣는다.
 * dataKey="stars"는 data 배열 각 객체의 stars 값을 선으로 그리라는 뜻이다.
 */
export function StarChart({ fullName }: { fullName: string }) {
  const [points, setPoints] = useState<StarPoint[] | null>(null); // null = 불러오는 중
  const [failed, setFailed] = useState(false);

  // [문법] 의존성 배열 [fullName]: fullName이 바뀔 때마다 다시 실행한다(다른 레포로 이동한 경우)
  useEffect(() => {
    getStars(fullName, 30)
      .then((p) => setPoints(p ?? []))
      .catch(() => setFailed(true));
  }, [fullName]);

  // 상태별로 다른 화면: 실패 → 문구, 로딩 → 스켈레톤, 점이 1개 이하 → 선을 그릴 수 없음
  if (failed) return <p className="text-sm text-muted-foreground">스타 추이를 불러오지 못했습니다.</p>;
  if (points === null) return <div className="h-60 animate-pulse rounded-lg bg-muted" />;
  if (points.length < 2) return <p className="text-sm text-muted-foreground">스타 기록이 쌓이는 중입니다.</p>;

  return (
    // role="img" + aria-label: 그림을 볼 수 없는 사용자(스크린 리더)에게 차트가 무엇인지 알려 준다
    <div className="h-60 w-full" role="img" aria-label={`${fullName} 최근 30일 스타 추이`}>
      {/* ResponsiveContainer: 부모 상자 크기에 맞춰 차트 크기를 자동 조절한다(모바일 대응) */}
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          {/* var(--border) 같은 CSS 변수를 쓰면 다크 모드에서 색이 자동으로 바뀐다(globals.css의 테마 토큰) */}
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
          {/* "2026-10-26".slice(5) → "10-26": 축에는 월-일만 짧게 */}
          <XAxis dataKey="date" tickFormatter={(d: string) => d.slice(5)} tick={{ fontSize: 12 }} stroke="var(--muted-foreground)" />
          <YAxis
            width={56}
            domain={["auto", "auto"]} // 0부터가 아니라 데이터 범위에 맞춰 축을 잡아 변화가 잘 보이게 한다
            tickFormatter={(v: number) => v.toLocaleString()}
            tick={{ fontSize: 12 }}
            stroke="var(--muted-foreground)"
          />
          <Tooltip
            formatter={(v) => [Number(v).toLocaleString(), "스타"]}
            contentStyle={{ background: "var(--popover)", border: "1px solid var(--border)", borderRadius: 8 }}
          />
          {/* isAnimationActive={false}: 선이 그려지는 애니메이션을 끈다(그리는 도중 화면이 중간에 끊겨 보이는 것 방지) */}
          <Line type="monotone" dataKey="stars" stroke="var(--primary)" strokeWidth={2} dot={false} isAnimationActive={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
