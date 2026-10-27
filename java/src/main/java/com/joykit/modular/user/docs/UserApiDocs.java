package com.joykit.modular.user.docs;

public final class UserApiDocs {
    private UserApiDocs() {
    }

    public static final String LIST_SUMMARY = "分页查询用户";
    public static final String LIST_DESCRIPTION =
        "按姓名、邮箱或电话进行不区分大小写的包含查询，按用户 ID 倒序返回；分页参数 pageIndex 从 1 开始，pageSize 默认 10、最大 1000；响应统一包含 code、message、data、timestamp，分页数据为 total、list。";

    public static final String GET_SUMMARY = "查询用户详情";
    public static final String GET_DESCRIPTION = "根据用户 ID 查询资料；用户不存在返回 HTTP 404。";

    public static final String CREATE_SUMMARY = "新增用户";
    public static final String CREATE_DESCRIPTION =
        "校验并保存用户资料，成功返回 HTTP 201 和 Location 响应头；联系电话重复返回 HTTP 409。";

    public static final String UPDATE_SUMMARY = "修改用户";
    public static final String UPDATE_DESCRIPTION =
        "完整更新用户资料；用户不存在返回 HTTP 404，联系电话重复返回 HTTP 409。";

    public static final String DELETE_SUMMARY = "删除用户";
    public static final String DELETE_DESCRIPTION =
        "永久删除指定用户并返回实际删除数量；用户不存在返回 HTTP 404，存在关联约束时返回 HTTP 409。";

    public static final String BATCH_DELETE_SUMMARY = "批量删除用户";
    public static final String BATCH_DELETE_DESCRIPTION =
        "每次提交 1～100 个正整数 ID，自动去重并返回实际删除数量；存在关联约束时回滚本次操作。";

    public static final String DELETE_ALL_SUMMARY = "删除全部用户";
    public static final String DELETE_ALL_DESCRIPTION =
        "危险操作，不受分页和搜索条件限制；confirmation 必须精确填写“删除全部用户”，存在关联约束时回滚本次操作。";
}
