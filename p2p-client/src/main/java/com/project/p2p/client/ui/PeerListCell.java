package com.project.p2p.client.ui;

import com.project.p2p.client.model.OnlinePeer;
import com.project.p2p.client.network.EncryptedPeerSession;
import com.project.p2p.client.network.PeerNetworkService;
import com.project.p2p.client.ui.common.AppIcons;
import com.project.p2p.client.ui.common.AvatarView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Modern peer contact list cell adhering to GitHub Primer design foundations.
 * Features deterministic avatar colors, online presence dot, endpoint badge,
 * and encrypted session indicators.
 */
public class PeerListCell extends ListCell<OnlinePeer> {

    private final PeerNetworkService peerNetworkService;

    public PeerListCell(PeerNetworkService peerNetworkService) {
        this.peerNetworkService = peerNetworkService;
        getStyleClass().add("peer-list-cell");
    }

    @Override
    protected void updateItem(OnlinePeer item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        // Avatar with online status
        AvatarView avatar = new AvatarView(item.username(), 36, true, true);

        // Name and endpoint labels
        Label nameLabel = new Label(item.username());
        nameLabel.getStyleClass().add("peer-cell-name");

        String endpointStr = item.observedIp() + ":" + item.listeningPort();
        Label addrLabel = new Label(endpointStr);
        addrLabel.getStyleClass().add("peer-cell-address");

        VBox textContainer = new VBox(2, nameLabel, addrLabel);
        textContainer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textContainer, Priority.ALWAYS);

        HBox row = new HBox(10, avatar, textContainer);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 6, 4, 6));

        // Check if session is actively established with this peer
        if (peerNetworkService != null) {
            EncryptedPeerSession session = peerNetworkService.getSession(item.username());
            if (session != null && session.isSecureEstablished()) {
                Node lockIcon = AppIcons.create(AppIcons.Icon.LOCK, 14, Color.web("#3fb950"));
                Tooltip.install(lockIcon, new Tooltip("Active End-to-End Encrypted Session"));
                row.getChildren().add(lockIcon);
            }
        }

        setGraphic(row);
        setText(null);
    }
}
