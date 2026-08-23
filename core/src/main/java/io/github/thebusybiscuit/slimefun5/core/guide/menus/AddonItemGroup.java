package io.github.thebusybiscuit.slimefun5.core.guide.menus;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun5.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun5.core.guide.GuideHistory;
import io.github.thebusybiscuit.slimefun5.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun5.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun5.core.guide.widgets.GuideWidget;
import io.github.thebusybiscuit.slimefun5.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.guide.SurvivalSlimefunGuide;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import io.github.thebusybiscuit.slimefun5.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.MaterialCompat;

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;

/**
 * One addon's single entry in the classic guide layout: a menu listing that addon's own menus, with the
 * addon's {@link GuideWidget info widgets} along the bottom row.
 * <p>
 * Unlike {@link io.github.thebusybiscuit.slimefun5.api.items.groups.NestedItemGroup} this holds arbitrary
 * {@link ItemGroup}s rather than only
 * {@link io.github.thebusybiscuit.slimefun5.api.items.groups.SubItemGroup}s, which is what lets the guide
 * fold an addon's existing top-level groups under one root without the addon changing how it declares
 * them.
 */
public class AddonItemGroup extends FlexItemGroup {

    private static final int PAGE_SIZE = 36;
    private static final int CONTENT_START = 9;
    private static final int BACK_SLOT = 1;
    private static final int PREV_SLOT = 46;
    private static final int NEXT_SLOT = 52;

    /** Free bottom-row slots between the pagination buttons. */
    private static final int[] WIDGET_SLOTS = { 47, 48, 49, 50, 51 };

    private final String addon;
    private final List<ItemGroup> members = new ArrayList<>();

    @ParametersAreNonnullByDefault
    public AddonItemGroup(NamespacedKey key, ItemStack item, String addon) {
        super(key, item, 3);
        this.addon = addon;
    }

    /** The addon this menu represents; also the key its widgets are looked up by. */
    @Nonnull
    public String getAddonName() {
        return addon;
    }

    public void addMember(@Nonnull ItemGroup group) {
        members.add(group);
    }

    @Nonnull
    public List<ItemGroup> getMembers() {
        return new ArrayList<>(members);
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean isVisible(Player p, PlayerProfile profile, SlimefunGuideMode mode) {
        return mode == SlimefunGuideMode.SURVIVAL_MODE && !members.isEmpty();
    }

    @Override
    @ParametersAreNonnullByDefault
    public void open(Player p, PlayerProfile profile, SlimefunGuideMode mode) {
        // A single member with no widgets to show would make this menu a one-tile dead end, so skip it.
        if (members.size() == 1 && Slimefun.getGuideWidgets().getForAddon(addon).isEmpty()) {
            SlimefunGuide.openItemGroup(profile, members.get(0), mode, 1);
            return;
        }

        openPage(p, profile, mode, 1);
    }

    @ParametersAreNonnullByDefault
    private void openPage(Player p, PlayerProfile profile, SlimefunGuideMode mode, int page) {
        GuideHistory history = profile.getGuideHistory();

        if (mode == SlimefunGuideMode.SURVIVAL_MODE) {
            history.add(this, page);
        }

        ChestMenu menu = new ChestMenu(Slimefun.getLocalization().getMessage(p, "guide.title.main"));
        SurvivalSlimefunGuide guide = (SurvivalSlimefunGuide) Slimefun.getRegistry().getSlimefunGuide(mode);

        menu.setEmptySlotsClickable(false);
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_BUTTON_CLICK_SOUND::playFor);
        guide.createHeader(p, profile, menu);

        menu.addItem(BACK_SLOT, ChestMenuUtils.getBackButton(p, "", ChatColor.GRAY + Slimefun.getLocalization().getMessage(p, "guide.back.guide")));
        menu.addMenuClickHandler(BACK_SLOT, (pl, s, is, action) -> {
            if (mode == SlimefunGuideMode.SURVIVAL_MODE && history.size() > 1) {
                history.goBack(guide);
            } else {
                SlimefunGuide.openMainMenu(profile, mode, history.getMainMenuPage());
            }
            return false;
        });

        List<ItemGroup> visible = visibleMembers(p);
        int offset = (page - 1) * PAGE_SIZE;
        int index = CONTENT_START;

        for (int i = offset; i < visible.size() && index < CONTENT_START + PAGE_SIZE; i++) {
            ItemGroup group = visible.get(i);

            menu.addItem(index, group.getItem(p));
            menu.addMenuClickHandler(index, (pl, slot, item, action) -> {
                SlimefunGuide.openItemGroup(profile, group, mode, 1);
                return false;
            });

            index++;
        }

        int pages = Math.max(1, (visible.size() - 1) / PAGE_SIZE + 1);

        menu.addItem(PREV_SLOT, ChestMenuUtils.getPreviousButton(p, page, pages));
        menu.addMenuClickHandler(PREV_SLOT, (pl, slot, item, action) -> {
            if (page > 1) {
                openPage(p, profile, mode, page - 1);
            }
            return false;
        });

        menu.addItem(NEXT_SLOT, ChestMenuUtils.getNextButton(p, page, pages));
        menu.addMenuClickHandler(NEXT_SLOT, (pl, slot, item, action) -> {
            if (page < pages) {
                openPage(p, profile, mode, page + 1);
            }
            return false;
        });

        placeWidgets(menu, p, profile);
        menu.open(p);
    }

    @Nonnull
    private List<ItemGroup> visibleMembers(@Nonnull Player p) {
        List<ItemGroup> visible = new ArrayList<>();

        for (ItemGroup group : members) {
            try {
                if (!group.isHidden(p)) {
                    visible.add(group);
                }
            } catch (Exception | LinkageError ignored) {
                // A broken member must not blank out the whole addon menu.
            }
        }

        return visible;
    }

    @ParametersAreNonnullByDefault
    private void placeWidgets(ChestMenu menu, Player p, PlayerProfile profile) {
        List<GuideWidget> widgets = Slimefun.getGuideWidgets().getForAddon(addon);

        for (int i = 0; i < widgets.size() && i < WIDGET_SLOTS.length; i++) {
            GuideWidget widget = widgets.get(i);

            menu.replaceExistingItem(WIDGET_SLOTS[i], CustomItemStack.create(MaterialCompat.stack(widget.getIcon()),
                ChatColor.translateAlternateColorCodes('&', widget.getDefaultName()),
                "",
                Slimefun.getLocalization().getMessage(p, "guide.categories-meta.open")));
            menu.addMenuClickHandler(WIDGET_SLOTS[i], (pl, slot, item, action) -> {
                widget.open(pl, profile);
                return false;
            });
        }

        if (widgets.size() > WIDGET_SLOTS.length) {
            Slimefun.logger().warning("[Guide] Addon " + addon + " declares " + widgets.size()
                + " guide widgets but only " + WIDGET_SLOTS.length + " fit on its menu; the rest are not shown.");
        }
    }
}
