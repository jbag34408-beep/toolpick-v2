"use client";

import Link from "next/link";
import { useState } from "react";
import { cn } from "@/lib/utils";

export type AccordionCategory = { slug: string; nameKo: string; count: number; image: string; tagline: string };

/**
 * 홈 상단의 카테고리 이미지 아코디언. 마우스를 올리거나 키보드로 포커스하면 그 카테고리가 넓게 펼쳐지고,
 * 누르면 카테고리 Top 10으로 이동한다.
 * 좁은 화면(md 미만)에서는 펼침 대신 2열 타일로 보여준다(가는 세로 막대는 손가락으로 누르기 어렵다).
 */
export function CategoryAccordion({ categories }: { categories: AccordionCategory[] }) {
  const [active, setActive] = useState(0);

  return (
    <ul className="grid grid-cols-2 gap-2 md:flex md:h-[420px] md:gap-3">
      {categories.map((c, i) => {
        const isActive = i === active;
        return (
          <li
            key={c.slug}
            className={cn(
              "relative h-28 overflow-hidden rounded-2xl bg-muted md:h-auto md:min-w-0 md:transition-[flex-grow] md:duration-700 md:ease-in-out",
              isActive ? "md:grow-[6]" : "md:grow"
            )}
            onMouseEnter={() => setActive(i)}
          >
            <Link href={`/category/${c.slug}`} className="block h-full outline-none focus-visible:ring-3 focus-visible:ring-ring/60" onFocus={() => setActive(i)}>
              {/* 글자가 이름을 알려 주므로 이미지는 장식(alt="") */}
              {/* eslint-disable-next-line @next/next/no-img-element -- 외부 이미지 몇 장이라 next/image 최적화 설정을 따로 두지 않는다 */}
              <img src={c.image} alt="" loading="lazy" className="absolute inset-0 size-full object-cover" />
              <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/25 to-black/10" />

              {/* 펼쳐진 상태 (모바일은 항상 이 모양) */}
              <div
                className={cn(
                  "absolute inset-x-0 bottom-0 p-3 text-white transition-opacity duration-500 md:p-5",
                  isActive ? "opacity-100" : "md:pointer-events-none md:opacity-0"
                )}
              >
                <span className="glass inline-block rounded-full border-white/30 bg-white/15 px-2.5 py-0.5 text-xs font-medium text-white">
                  {c.count > 0 ? `Top ${c.count}` : "순위 준비 중"}
                </span>
                <p className="mt-1 text-sm font-bold md:mt-2 md:text-2xl">{c.nameKo}</p>
                <p className="hidden text-sm text-white/85 md:block">{c.tagline}</p>
              </div>

              {/* 접힌 상태: 세로로 눕힌 이름 (넓은 화면에서만) */}
              <span
                className={cn(
                  "absolute bottom-24 left-1/2 hidden -translate-x-1/2 rotate-90 text-sm font-semibold whitespace-nowrap text-white transition-opacity duration-300 md:block",
                  isActive ? "opacity-0" : "opacity-100"
                )}
                aria-hidden
              >
                {c.nameKo}
              </span>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
