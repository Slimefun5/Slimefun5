package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

/**
 * The sandbox view where {@code InventoryView} is a class that can be retitled (1.19.4 to 1.20.6).
 *
 * @implNote Separate from {@link DeclaredTitleView} because core retitles a window by looking {@code setTitle}
 *           up reflectively, so a sandbox view on 1.14 to 1.19.3 must not have it, as the server's views do not.
 */
public final class RetitlableView extends DeclaredTitleView {

    RetitlableView(SandboxInventoryView.Backing backing) {
        super(backing);
    }

    public String getOriginalTitle() {
        return backing.getOriginalTitle();
    }

    public void setTitle(String title) {
        backing.setTitle(title);
    }
}
