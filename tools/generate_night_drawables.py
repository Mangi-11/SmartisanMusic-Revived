#!/usr/bin/env python3
"""生成锤子音乐深色模式所需的夜间位图变体。

从 app/src/main/res/drawable-nodpi 与 drawable-xxhdpi 读取原版 PNG，按下方
RULES 逐文件变换后写入 drawable-night-nodpi 与 drawable-night-xxhdpi（同名同
密度）。资源系统会在系统深色模式下自动选用同名夜间变体，因此选择器 XML 与
代码无需任何分支。

变换原则（方案来自 BigBang 项目的 android-dark-mode 技能）：
- multiply_to : RGB 逐通道压到目标色，保留 alpha。用于
  纯白/浅色底图（栏、卡片、弹层、平铺背景），保留底图原有明暗层次。目标色板
  对齐锤子天气炭灰系（页底 #25282D、标题栏 #292C31、卡片 #34373C、
  较高表面 #41464D、分隔线 #3A3D42）。
- 播放控件：圆盘、图标、描边、阴影和抗锯齿像素作为整图连续变换，不拆层重绘；
  收藏红色通过连续色彩权重保留，不做区域阈值切割。
- whiteify : RGB 置 255、保留 alpha（可选 alpha 倍增）。用于以 alpha 承载形状
  的深色线条/箭头/剪影图标。disabled 态原图 alpha 更低，whiteify 后自然更暗。
- 开关（switch_ex_*）：8.1.0 原版自带 _dark 系列深色资产（Smartisan OS 深色
  主题资源），直接按 COPIES 复制为夜间变体，SwitchEx 以 R.drawable 同名加载，
  无需接线 STYLE_DARK。
- 不处理：阴影/蒙版（lum=0 低 alpha）、其他品牌红/蓝/黄彩色态、黑胶唱盘、
  深灰占位封面、启动图标、死资源。

nine-patch 的最外圈 1px 边框（拉伸标记）逐像素原样保留。
脚本幂等：重复运行产出一致。运行后自动生成深色底 contact sheet 到
build/night_drawables_preview/ 供目检。
"""

from pathlib import Path

from PIL import Image, ImageDraw

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"
PREVIEW_DIR = Path(__file__).resolve().parent.parent / "build/night_drawables_preview"

XXHDPI = "drawable-xxhdpi"
NODPI = "drawable-nodpi"


def multiply_to(target, base=255):
    """把近白色底图按目标 RGB 等比压暗（逐通道 factor = target/base），
    保留底图原有明暗层次。target 为 (r, g, b)，base 为单值或三元组。"""
    if isinstance(base, (int, float)):
        base = (base, base, base)
    factors = tuple(t / b for t, b in zip(target, base))
    def fn(r, g, b, a):
        return (min(255, round(r * factors[0])), min(255, round(g * factors[1])),
                min(255, round(b * factors[2])), a)
    return fn


def whiteify(alpha_scale=1.0, level=255):
    def fn(r, g, b, a):
        return (level, level, level, min(255, round(a * alpha_scale)))
    return fn


def multiply_to_preserving_red(target, base=255):
    """整图压暗中性色，并把预混在浅色圆面上的红色 AA 边缘重新合成到夜间圆面。

    收藏红心主体色为 #E65C53；边缘像素已在原图中与近白圆面预混，不能直接保留，
    否则会在深色圆面上形成浅粉光边。以红色相对中性色的色度估算覆盖率，主体红色
    原样保留，部分覆盖像素连续合成到 target，不改变原图 alpha 或几何。
    """
    neutral = multiply_to(target, base)
    heart = (230, 92, 83)
    heart_chroma = heart[0] - max(heart[1], heart[2])

    def fn(r, g, b, a):
        nr, ng, nb, _ = neutral(r, g, b, a)
        coverage = max(0.0, min(1.0, (r - max(g, b)) / heart_chroma))
        if coverage >= 1.0:
            return (r, g, b, a)
        if coverage == 0.0:
            return (nr, ng, nb, a)
        tr, tg, tb, _ = neutral(base, base, base, a)
        return (
            round(tr * (1 - coverage) + heart[0] * coverage),
            round(tg * (1 - coverage) + heart[1] * coverage),
            round(tb * (1 - coverage) + heart[2] * coverage),
            a,
        )
    return fn


PAGE = (37, 40, 45)        # #25282D 页底
TITLE_BAR = (41, 44, 49)   # #292C31 标题栏
SURFACE = (52, 55, 60)     # #34373C 卡片/面板
RAISED = (65, 70, 77)      # #41464D 较高表面/输入框
DIVIDER = (58, 61, 66)     # #3A3D42 分隔线

RULES = {
    XXHDPI: {
        # ---- 浅色实底 → 炭灰 ----
        "common_bg.png": multiply_to(PAGE, base=242),            # 全页平铺纹理
        "menu_dialog_background.png": multiply_to(SURFACE, base=245),
        "sb_tabbar_bg.png": multiply_to(TITLE_BAR, base=251),
        "bottom_sheet_title_bar_bg.9.png": multiply_to(TITLE_BAR, base=253),
        "smartlist_header_bg.png": multiply_to(TITLE_BAR, base=246),
        "list_title_bg.png": multiply_to(TITLE_BAR, base=246),
        "home_recommend_title_noline_bg.9.png": multiply_to(TITLE_BAR, base=246),
        "letters_bar_background.9.png": multiply_to(SURFACE, base=249),
        "list_item_normal.9.png": multiply_to(SURFACE),
        "group_list_top.9.png": multiply_to(SURFACE),
        "group_list_mid.9.png": multiply_to(SURFACE),
        "group_list_bottom.9.png": multiply_to(SURFACE),
        "action_menu_grid_bg.9.png": multiply_to(SURFACE),
        "action_menu_grid_bg_pressed.9.png": multiply_to(RAISED, base=244),
        "pop_up_menu_bg.9.png": multiply_to(SURFACE),
        "time_picker_widget_bg.9.png": multiply_to(RAISED, base=245),
        "time_picker_widget_bottom.png": multiply_to(SURFACE, base=225),
        "search_field.9.png": multiply_to(RAISED, base=245),
        "line_between.png": multiply_to(DIVIDER, base=239),
        # ---- 深色线条/剪影图标 → 白 ----
        "back_icon_normal.png": whiteify(),
        "back_icon_pressed.png": whiteify(),
        "back_icon_disabled.png": whiteify(),
        "titlebar_icon_delete_nor.png": whiteify(),
        "titlebar_icon_delete_pre.png": whiteify(),
        "titlebar_icon_delete_dis.png": whiteify(),
        "standard_icon_cancel.png": whiteify(),
        "standard_icon_cancel_pressed.png": whiteify(),
        "standard_icon_complete.png": whiteify(),
        "standard_icon_complete_pressed.png": whiteify(),
        "standard_icon_complete_disabled.png": whiteify(),
        "standard_icon_multi_select.png": whiteify(),
        "standard_icon_multi_select_pressed.png": whiteify(),
        "standard_icon_multi_select_disabled.png": whiteify(),
        "icon_setting_normal.png": whiteify(),
        "icon_setting_pressed.png": whiteify(),
        "icon_sort_normal.png": whiteify(),
        "icon_sort_pressed.png": whiteify(),
        "icon_sort_disable.png": whiteify(),
        "search_normal.png": whiteify(),
        "search_pressed.png": whiteify(),
        "search_disabled.png": whiteify(),
        "search_bar_left_icon.png": whiteify(),
        "btn_more.png": whiteify(),
        "arrow3.png": whiteify(),
        "list_arrow.png": whiteify(),
        "header_remove.png": whiteify(),
        "header_remove_down.png": whiteify(),
        "name_editor_icon.png": whiteify(),
        "btn_deletelist2.png": whiteify(),
        "btn_deletelist2_down.png": whiteify(),
        "btn_deletelist2_disable.png": whiteify(),
        "btn_editlist2.png": whiteify(),
        "btn_editlist2_down.png": whiteify(),
        "btn_editlist2_disable.png": whiteify(),
        "btn_shuffle2.png": whiteify(),
        "btn_shuffle2_down.png": whiteify(),
        "btn_shuffle2_disabled.png": whiteify(),
        "btn_shuffle3.png": whiteify(),
        "btn_shuffle3_down.png": whiteify(),
        "btn_shuffle3_disabled.png": whiteify(),
        "check_box_off.png": whiteify(),
        "unselected.png": whiteify(),
        "albums_selected_large_empty.9.png": whiteify(),
        "compose_quicktext_delete.png": whiteify(),
        "compose_quicktext_delete_down.png": whiteify(),
        "blank_style.png": whiteify(alpha_scale=1.5),
        "filter_btn_left.9.png": whiteify(level=64),
        "filter_btn_left_pressed.9.png": multiply_to((85, 88, 94), base=177),
        "filter_btn_middle.9.png": whiteify(level=64),
        "filter_btn_middle_pressed.9.png": multiply_to((85, 88, 94), base=177),
        "filter_btn_right.9.png": whiteify(level=64),
        "filter_btn_right_pressed.9.png": multiply_to((85, 88, 94), base=177),
        "title_button_normal_bg.9.png": whiteify(),
        "title_button_pressed_bg.9.png": whiteify(),
        "title_button_disable_bg.9.png": whiteify(),
        "btn_playing_cycle_off.9.png": whiteify(),
        "btn_playing_cycle_on.9.png": whiteify(),
        "btn_playing_repeat_on.9.png": whiteify(),
        "btn_playing_shuffle_off.9.png": whiteify(),
        "btn_playing_shuffle_on.9.png": whiteify(),
        "letters_bar_highlight_icon.png": whiteify(),
        "letters_bar_arrow.png": whiteify(),
        "local_phone_icon.png": whiteify(),
        "playing_progress_bar_line.png": whiteify(),
        # 播放页实体控件整图连续压暗，保留原图抗锯齿、描边、图标与阴影关系。
        # 灰阶按“中心按钮 > 两侧按钮 > 背景”分层，并让音量滑块与中心按钮同阶，
        # 避免低层级进度条比主操作更抢眼，或冷蓝圆面看起来像禁用态。
        "btn_playing_play.png": multiply_to((150, 151, 154), base=253),
        "btn_playing_play_down.png": multiply_to((126, 127, 130), base=240),
        "btn_playing_pause.png": multiply_to((150, 151, 154), base=253),
        "btn_playing_pause_down.png": multiply_to((126, 127, 130), base=240),
        "btn_playing_next.png": multiply_to((132, 133, 136), base=253),
        "btn_playing_next_down.png": multiply_to((112, 113, 116), base=240),
        "btn_playing_prev.png": multiply_to((132, 133, 136), base=253),
        "btn_playing_prev_down.png": multiply_to((112, 113, 116), base=240),
        "playing_control_volume.png": multiply_to((150, 151, 154), base=254),
        # 原轨道以极低 alpha 黑色绘制；夜间适度提高亮度和 alpha，轮廓保持低于填充段。
        "progressbar_volume_bg.9.png": whiteify(alpha_scale=1.25, level=170),
        "progressbar_volume.9.png": multiply_to((140, 141, 144), base=170),
        "progressbar_time_bg.9.png": whiteify(alpha_scale=1.25, level=170),
        "progressbar_time.9.png": multiply_to((140, 141, 144), base=170),
        # 底部播放条按钮同样整图变换，不拆分圆盘和图标。
        "floatplay_btn_favorite_add.png": multiply_to((132, 133, 136), base=253),
        "floatplay_btn_favorite_add_down.png": multiply_to((112, 113, 116), base=238),
        "floatplay_btn_favorite_cancel.png": multiply_to_preserving_red((132, 133, 136), base=253),
        "floatplay_btn_favorite_cancel_down.png": multiply_to_preserving_red((112, 113, 116), base=238),
        "thumbnail_floatplay_btn_prev.png": multiply_to((132, 133, 136), base=253),
        "thumbnail_floatplay_btn_prev_down.png": multiply_to((112, 113, 116), base=238),
        "floatplay_btn_play.png": multiply_to((132, 133, 136), base=253),
        "floatplay_btn_play_down.png": multiply_to((112, 113, 116), base=238),
        "floatplay_btn_pause.png": multiply_to((132, 133, 136), base=253),
        "floatplay_btn_pause_down.png": multiply_to((112, 113, 116), base=238),
        "floatplay_btn_next.png": multiply_to((132, 133, 136), base=253),
        "floatplay_btn_next_down.png": multiply_to((112, 113, 116), base=238),
        "revone_smartisan_list_popup_menu_separator.png": whiteify(),
        # 弹层按压态：日间为黑色压暗，夜间改为低透明白提亮（alpha 缩减避免过强）
        "revone_smartisan_list_popup_menu_pressed.9.png": whiteify(alpha_scale=0.35),
    },
    NODPI: {
        # ---- 浅色实底 → 炭灰 ----
        "ablum_crosstexture_bg.png": multiply_to(PAGE, base=234),
        "titlebar_bg.9.png": multiply_to(TITLE_BAR, base=246),
        "titlebar_playing_bg.png": multiply_to(TITLE_BAR, base=246),
        "mask_playing_lyric.png": multiply_to(PAGE, base=236),
        # ---- 深色线条/剪影图标 → 白 ----
        "btn_current_playing_back_normal.png": whiteify(),
        "btn_current_playing_back_pressed.png": whiteify(),
        "btn_current_playing_back_disable.png": whiteify(),
        "btn_current_playing_check_normal.png": whiteify(),
        "btn_current_playing_check_pressed.png": whiteify(),
        "btn_current_playing_check_disable.png": whiteify(),
        "more_select_icon_addlist.png": whiteify(),
        "more_select_icon_addlist_down.png": whiteify(),
        "more_select_icon_addplay.png": whiteify(),
        "more_select_icon_addplay_down.png": whiteify(),
        "more_select_icon_delete.png": whiteify(),
        "more_select_icon_djing.png": whiteify(),
        "more_select_icon_favorite_add.png": whiteify(),
        "more_select_icon_favorite_add_down.png": whiteify(),
        "more_select_icon_lyric.png": whiteify(),
        "more_select_icon_share.png": whiteify(),
        "more_select_icon_share_down.png": whiteify(),
        "more_select_icon_timer.png": whiteify(),
        "playing_btn_favorite_add.png": whiteify(),
        "playing_btn_favorite_add_down.png": whiteify(),
        "playing_control_time.png": multiply_to((150, 151, 154), base=252),
        "more_btn.png": whiteify(),
        "more_btn_down.png": whiteify(),
        "btn_playing_back.png": whiteify(),
        "btn_playing_back_down.png": whiteify(),
        "btn_shuffle3.png": whiteify(),
        "search_badge_grey.9.png": whiteify(),
        "search_badge_grey_p.9.png": whiteify(),
        # 空态剪影：原 alpha≈23 极淡，夜间略提亮保持可见
        "blank_folder.png": whiteify(alpha_scale=1.5),
        "blank_playlist.png": whiteify(alpha_scale=1.5),
        "blank_search.png": whiteify(alpha_scale=1.5),
        "blank_song.png": whiteify(alpha_scale=1.5),
    },
}

# 8.1.0 原版深色开关资产，直接复制为夜间变体（dst: src）
COPIES = {
    XXHDPI: {
        "switch_ex_bottom.png": "switch_ex_bottom_dark.png",
        "switch_ex_unpressed.png": "switch_ex_unpressed_dark.png",
        "switch_ex_pressed.png": "switch_ex_pressed_dark.png",
        "switch_ex_frame.png": "switch_ex_frame_dark.png",
        "switch_ex_frame_pressed.png": "switch_ex_frame_pressed_dark.png",
        "switch_ex_mask.png": "switch_ex_mask_dark.png",
    },
}

def night_dir(qualifier: str) -> str:
    return qualifier.replace("drawable-", "drawable-night-")


def apply_rules():
    missing = []
    generated = 0
    for qualifier, rules in RULES.items():
        src_dir = RES / qualifier
        dst_dir = RES / night_dir(qualifier)
        dst_dir.mkdir(parents=True, exist_ok=True)
        for name, fn in sorted(rules.items()):
            src = src_dir / name
            if not src.exists():
                missing.append(f"{qualifier}/{name}")
                continue
            im = Image.open(src).convert("RGBA")
            w, h = im.size
            nine = name.endswith(".9.png")
            out = Image.new("RGBA", (w, h))
            src_px, out_px = im.load(), out.load()
            x0, y0 = (1, 1) if nine else (0, 0)
            x1, y1 = (w - 1, h - 1) if nine else (w, h)
            for y in range(y0, y1):
                for x in range(x0, x1):
                    out_px[x, y] = fn(*src_px[x, y])
            if nine:  # nine-patch 边框逐像素原样保留
                for x in range(w):
                    out_px[x, 0] = src_px[x, 0]
                    out_px[x, h - 1] = src_px[x, h - 1]
                for y in range(h):
                    out_px[0, y] = src_px[0, y]
                    out_px[w - 1, y] = src_px[w - 1, y]
            out.save(dst_dir / name, optimize=True)
            generated += 1
            print(f"generated {night_dir(qualifier)}/{name}")
    for qualifier, copies in COPIES.items():
        src_dir = RES / qualifier
        dst_dir = RES / night_dir(qualifier)
        dst_dir.mkdir(parents=True, exist_ok=True)
        for dst_name, src_name in sorted(copies.items()):
            src = src_dir / src_name
            if not src.exists():
                missing.append(f"{qualifier}/{src_name}")
                continue
            Image.open(src).save(dst_dir / dst_name, optimize=True)
            generated += 1
            print(f"copied    {night_dir(qualifier)}/{dst_name} <- {src_name}")
    if missing:
        raise SystemExit(f"源文件缺失: {missing}")
    print(f"完成，共 {generated} 个夜间变体")


def make_contact_sheets():
    """把夜间变体拼到炭灰页底上目检（带文件名标签）。"""
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    cell, label_h, cols = 120, 16, 6
    for qualifier in RULES:
        dst_dir = RES / night_dir(qualifier)
        names = sorted(p.name for p in dst_dir.glob("*.png"))
        if not names:
            continue
        rows = (len(names) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * cell, rows * (cell + label_h)), PAGE)
        draw = ImageDraw.Draw(sheet)
        for i, name in enumerate(names):
            im = Image.open(dst_dir / name).convert("RGBA")
            im.thumbnail((cell - 12, cell - 12), Image.LANCZOS)
            cx = (i % cols) * cell
            cy = (i // cols) * (cell + label_h)
            sheet.paste(im, (cx + (cell - im.width) // 2,
                             cy + (cell - im.height) // 2), im)
            draw.text((cx + 4, cy + cell + 2), name[:22], fill=(159, 161, 164))
        out = PREVIEW_DIR / f"{night_dir(qualifier)}_sheet.png"
        sheet.save(out)
        print(f"contact sheet -> {out}")


if __name__ == "__main__":
    apply_rules()
    make_contact_sheets()
