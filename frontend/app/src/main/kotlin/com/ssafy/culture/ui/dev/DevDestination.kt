package com.ssafy.culture.ui.dev

enum class DevGroup(val titleKr: String) {
    Auth("인증"),
    Main("메인 탭"),
    Story("스토리 만들기"),
    Quest("퀘스트"),
    Result("결과·이북"),
    Profile("프로필"),
}

enum class DevDestination(
    val label: String,
    val route: String,
    val group: DevGroup,
    val requiresAuth: Boolean = true,
) {
    Login("로그인", "login", DevGroup.Auth, requiresAuth = false),
    Signup("회원가입", "signup", DevGroup.Auth, requiresAuth = false),
    PermissionOnboarding("권한 온보딩", "permissionOnboarding", DevGroup.Auth, requiresAuth = false),

    Home("홈", "home", DevGroup.Main),
    Map("지도", "map", DevGroup.Main),
    Story("스토리(갤러리)", "story", DevGroup.Main),
    Profile("프로필", "profile", DevGroup.Main),

    PeopleCount("주인공 정보", "peopleCount", DevGroup.Story),
    PersonalitySurvey("성향 선택", "personalitySurvey", DevGroup.Story),
    CourseSelect("코스 선택", "courseSelect", DevGroup.Story),
    CourseOrder("코스 순서(courseId=1)", "courseOrder/1", DevGroup.Story),

    QuestRoute("퀘스트 동선", "questRoute", DevGroup.Quest),
    QuestDetail("퀘스트 상세", "questDetail", DevGroup.Quest),
    QuestCamera("퀘스트 카메라(0/0)", "questCamera/0/0", DevGroup.Quest),
    VisionValidation("AI 비전 검증", "questCameraValidation", DevGroup.Quest),

    ResultComplete("결과 완료", "resultComplete", DevGroup.Result),
    StoryBookViewer("이북 뷰어(latest)", "storyBookViewer/latest", DevGroup.Result),

    EditProfile("프로필 편집", "editProfile", DevGroup.Profile),
    Settings("설정", "settings", DevGroup.Profile),
}
