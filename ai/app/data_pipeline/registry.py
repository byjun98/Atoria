"""
Registry of the 15 selected cultural heritage sites.

Provides:
- ``SELECTED_HERITAGES``: the canonical list of HeritageEntity objects.
- ``make_heritage_id()``: slug generator.
- ``find_heritage_by_text()``: alias-based text matching.
- Vocabulary lists for entity linking (places, people).
"""
from __future__ import annotations

from app.schemas.data.heritage_entity import HeritageEntity

# ---------------------------------------------------------------------------
# 15 selected heritages
# ---------------------------------------------------------------------------

SELECTED_HERITAGES: list[HeritageEntity] = [
    HeritageEntity(
        heritage_id="cheomseongdae",
        canonical_name="첨성대",
        aliases=["瞻星臺", "점성대"],
        designation="국보",
        era="신라",
        summary="선덕여왕 때 축조된 동아시아 현존 최고(最古)의 천문 관측대.",
    ),
    HeritageEntity(
        heritage_id="seokbinggo",
        canonical_name="석빙고",
        aliases=["石氷庫", "경주 석빙고"],
        designation="보물",
        era="조선",
        summary="조선시대 석조 얼음 저장고.",
    ),
    HeritageEntity(
        heritage_id="donggung-wolji",
        canonical_name="동궁과월지",
        aliases=["동궁과 월지", "안압지", "임해전지", "雁鴨池"],
        designation="사적",
        era="신라",
        summary="신라 왕궁의 별궁과 인공 연못 유적.",
    ),
    HeritageEntity(
        heritage_id="dabotap",
        canonical_name="불국사 다보탑",
        aliases=["多寶塔", "다보탑"],
        designation="국보",
        era="신라",
        summary="불국사 대웅전 앞 동쪽의 석탑으로 화려한 장식이 특징.",
    ),
    HeritageEntity(
        heritage_id="seokgatap",
        canonical_name="불국사 석가탑",
        aliases=["釋迦塔", "석가탑", "무영탑", "無影塔"],
        designation="국보",
        era="신라",
        summary="불국사 대웅전 앞 서쪽의 3층 석탑, 무구정광대다라니경 출토.",
    ),
    HeritageEntity(
        heritage_id="bulguksa-daeungjeon",
        canonical_name="불국사 대웅전",
        aliases=["大雄殿", "불국사대웅전"],
        designation="보물",
        era="신라",
        summary="불국사의 본전으로 석가모니불을 모신 전각.",
    ),
    HeritageEntity(
        heritage_id="baekungyo",
        canonical_name="불국사 백운교",
        aliases=["白雲橋", "백운교", "청운교", "靑雲橋", "불국사 청운교 백운교"],
        designation="국보",
        era="신라",
        summary="불국사 자하문으로 오르는 돌계단으로, 청운교와 함께 국보.",
    ),
    HeritageEntity(
        heritage_id="emille-bell",
        canonical_name="에밀레종",
        aliases=["성덕대왕신종", "聖德大王神鐘", "봉덕사종"],
        designation="국보",
        era="신라",
        summary="통일신라 경덕왕·혜공왕 대에 주조된 범종, 국내 최대 크기.",
    ),
    HeritageEntity(
        heritage_id="cheonmachong",
        canonical_name="천마총",
        aliases=["天馬塚", "경주 155호 고분"],
        designation="사적",
        era="신라",
        summary="천마도가 출토된 신라 고분, 대릉원 내 위치.",
    ),
    HeritageEntity(
        heritage_id="geumgwanchong",
        canonical_name="금관총",
        aliases=["金冠塚"],
        designation="사적",
        era="신라",
        summary="금관 및 금제 장신구가 출토된 신라 고분.",
    ),
    HeritageEntity(
        heritage_id="bonghwangdae",
        canonical_name="봉황대",
        aliases=["鳳凰臺", "봉황대 고분"],
        designation="사적",
        era="신라",
        summary="경주 시내 대형 고분 중 하나.",
    ),
    HeritageEntity(
        heritage_id="gyeongju-hyanggyo",
        canonical_name="경주향교",
        aliases=["鄕校", "경주 향교"],
        designation="사적",
        era="조선",
        summary="경주 지역의 조선시대 관립 교육기관.",
    ),
    HeritageEntity(
        heritage_id="goseonsa-samcheung-seoktap",
        canonical_name="경주 고선사지 삼층석탑",
        aliases=["高仙寺址", "고선사지 삼층석탑", "고선사지삼층석탑"],
        designation="국보",
        era="신라",
        summary="고선사 터에 남은 통일신라 초기 삼층석탑.",
    ),
    HeritageEntity(
        heritage_id="cheonmachong-gold-crown",
        canonical_name="천마총 금관",
        aliases=["天馬塚 金冠", "천마총금관"],
        designation="국보",
        era="신라",
        summary="천마총에서 출토된 신라 금관.",
    ),
    HeritageEntity(
        heritage_id="seondeok-queen-tomb",
        canonical_name="선덕여왕릉",
        aliases=["善德女王陵", "선덕왕릉"],
        designation="사적",
        era="신라",
        summary="신라 제27대 선덕여왕의 능.",
    ),
]

# Build look-up structures ------------------------------------------------

_HERITAGE_BY_ID: dict[str, HeritageEntity] = {
    h.heritage_id: h for h in SELECTED_HERITAGES
}

# Alias → HeritageEntity mapping (canonical name + all aliases)
_ALIAS_MAP: dict[str, HeritageEntity] = {}
for _h in SELECTED_HERITAGES:
    _ALIAS_MAP[_h.canonical_name] = _h
    for _a in _h.aliases:
        _ALIAS_MAP[_a] = _h


def get_heritage(heritage_id: str) -> HeritageEntity | None:
    """Look up a heritage entity by its slug id."""
    return _HERITAGE_BY_ID.get(heritage_id)


def find_heritages_in_text(text: str) -> list[HeritageEntity]:
    """Return all heritages whose canonical name or alias appears in *text*."""
    seen: set[str] = set()
    result: list[HeritageEntity] = []
    for name, entity in _ALIAS_MAP.items():
        if name in text and entity.heritage_id not in seen:
            seen.add(entity.heritage_id)
            result.append(entity)
    return result


# ---------------------------------------------------------------------------
# Known vocabulary for entity linking
# ---------------------------------------------------------------------------

KNOWN_PLACES: list[str] = [
    "경주", "불국사", "석굴암", "남산", "첨성대", "대릉원", "안압지", "황룡사",
    "반월성", "계림", "포석정", "월성", "명활산성", "분황사", "감은사",
    "동궁", "월지", "대능원", "보문", "천마총", "금관총", "봉황대",
    "남산", "토함산",
]

KNOWN_PEOPLE: list[str] = [
    "선덕여왕", "김유신", "문무왕", "원효", "의상",
    "석탈해", "박혁거세", "김알지", "경덕왕", "혜공왕", "진평왕",
    "탈해이사금", "호공", "알영부인", "설총", "자장",
    "아사달", "아사녀",
]

KNOWN_HERITAGES: list[str] = [
    h.canonical_name for h in SELECTED_HERITAGES
]
