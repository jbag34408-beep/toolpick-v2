import { Button as ButtonPrimitive } from "@base-ui/react/button"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "cn"

const buttonVariants = cva(
  // 유리(liquid glass) 스타일: 반투명 배경 + 뒤 배경 흐림 + 테두리 굴절 그림자. 모든 버튼·버튼 모양 링크가 이 값을 쓴다
  "group/button inline-flex shrink-0 cursor-pointer items-center justify-center rounded-full border border-transparent text-sm font-medium whitespace-nowrap backdrop-blur-md transition-all duration-300 outline-none select-none hover:scale-[1.03] focus-visible:ring-3 focus-visible:ring-ring/50 active:not-aria-[haspopup]:scale-[0.98] disabled:pointer-events-none disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        // 강조 버튼: 짙은 유리
        default:
          "bg-primary/85 text-primary-foreground shadow-md shadow-black/15 ring-1 ring-white/20 ring-inset hover:bg-primary/95",
        // 기본 유리 버튼
        outline: "liquid-shadow bg-white/35 text-foreground hover:bg-white/55 aria-expanded:bg-white/55",
        secondary: "liquid-shadow bg-white/55 text-secondary-foreground hover:bg-white/75",
        ghost: "text-foreground hover:bg-white/45 hover:liquid-shadow aria-expanded:bg-white/45",
        destructive:
          "liquid-shadow bg-destructive/10 text-destructive hover:bg-destructive/20 focus-visible:ring-destructive/20",
        link: "text-primary underline-offset-4 hover:scale-100 hover:underline",
      },
      size: {
        default:
          "h-9 gap-1.5 px-4 has-data-[icon=inline-end]:pr-3 has-data-[icon=inline-start]:pl-3",
        xs: "h-6 gap-1 px-2.5 text-xs [&_svg:not([class*='size-'])]:size-3",
        sm: "h-8 gap-1 px-3.5 text-[0.8rem] [&_svg:not([class*='size-'])]:size-3.5",
        lg: "h-11 gap-1.5 px-6 has-data-[icon=inline-end]:pr-4 has-data-[icon=inline-start]:pl-4",
        icon: "size-9",
        "icon-xs": "size-6 [&_svg:not([class*='size-'])]:size-3",
        "icon-sm": "size-8",
        "icon-lg": "size-11",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

function Button({
  className,
  variant = "default",
  size = "default",
  ...props
}: ButtonPrimitive.Props & VariantProps<typeof buttonVariants>) {
  return (
    <ButtonPrimitive
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}

export { Button, buttonVariants }
