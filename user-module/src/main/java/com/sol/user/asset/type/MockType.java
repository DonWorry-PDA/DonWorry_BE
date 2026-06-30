package com.sol.user.asset.type;

public enum MockType {
    NEED_IMPROVEMENT,
    NEED_COMPLEMENT,
    STABLE,
    SALARY_DEMO,
    // 현실 케이스 확장(realistic 세트) — 은퇴 여부·연금 충당률·부채·종목쏠림 등 다양한 축
    GROWTH_CONCENTRATED,   // 개별주 몰빵(60·은퇴) — 투자검진 쏠림·배당공백
    PRE_RETIREMENT,        // 은퇴 임박(59·미은퇴, 국민연금 미수령)
    STRUCTURAL_SHORTAGE,   // 구조적 부족(67·은퇴, 자산·연금 적음)
    PENSION_SUFFICIENT,    // 연금 충분 여유(70·은퇴, 국민연금이 생활비 충당)
    HIGH_DEBT,             // 고부채 위기(58·미은퇴, 부채 2억)
    PRIVATE_PENSION_RICH   // 사적연금 빵빵(63·은퇴, IRP·연금저축 비중↑)
}
