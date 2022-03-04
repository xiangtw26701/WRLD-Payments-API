package com.nftworlds.wallet.objects;

import com.nftworlds.wallet.NFTWorlds;
import com.nftworlds.wallet.contracts.wrappers.polygon.PolygonWRLDToken;
import com.nftworlds.wallet.objects.payments.PaymentRequest;
import com.nftworlds.wallet.objects.payments.PeerToPeerPayment;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class Wallet {

    @Getter
    private UUID associatedPlayer;
    @Getter
    private String address;
    @Getter
    @Setter
    private double polygonWRLDBalance;
    @Getter
    @Setter
    private double ethereumWRLDBalance;

    public Wallet(UUID associatedPlayer, String address) {
        this.associatedPlayer = associatedPlayer;
        this.address = address;

        //Get balance initially
        double polygonBalance = 0;
        double ethereumBalance = 0;
        try {
            BigInteger bigIntegerPoly = NFTWorlds.getInstance().getWrld().getPolygonBalance(address);
            BigInteger bigIntegerEther = NFTWorlds.getInstance().getWrld().getEthereumBalance(address);
            polygonBalance = Convert.fromWei(bigIntegerPoly.toString(), Convert.Unit.ETHER).doubleValue();
            ethereumBalance = Convert.fromWei(bigIntegerEther.toString(), Convert.Unit.ETHER).doubleValue();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        this.polygonWRLDBalance = polygonBalance;
        this.ethereumWRLDBalance = ethereumBalance;

    }

    /**
     * Get the wallet's WRLD balance
     */
    public double getWRLDBalance(Network network) {
        if (network == Network.POLYGON) {
            return polygonWRLDBalance;
        } else {
            return ethereumWRLDBalance;
        }
    }

    /**
     * Send a request for a WRLD transaction from this wallet
     *
     * @param amount
     * @param network
     * @param reason
     * @param canDuplicate
     * @param payload
     */
    public <T> void requestWRLD(double amount, Network network, String reason, boolean canDuplicate, T payload) {
        NFTWorlds nftWorlds = NFTWorlds.getInstance();
        NFTPlayer nftPlayer = NFTPlayer.getByUUID(associatedPlayer);
        if (nftPlayer != null) {
            Player player = Bukkit.getPlayer(nftPlayer.getUuid());
            if (player != null) {
                Uint256 refID = new Uint256(new BigInteger(256, new Random())); //NOTE: This generates a random Uint256 to use as a reference. Don't know if we want to change this or not.
                long timeout = Instant.now().plus(nftWorlds.getNftConfig().getLinkTimeout(), ChronoUnit.SECONDS).toEpochMilli();
                new PaymentRequest(associatedPlayer, amount, refID, network, reason, timeout, canDuplicate, payload);
                String paymentLink = "https://nftworlds.com/pay/?to=" + nftWorlds.getNftConfig().getServerWalletAddress() + "&amount=" + amount + "&ref=" + refID.getValue().toString() + "&expires=" + (int) (timeout / 1000) + "&duplicate="+canDuplicate;
                player.sendMessage(ChatColor.GOLD + "Incoming payment request for: " + ChatColor.WHITE + reason);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f&lPAY HERE: ") + ChatColor.GREEN + paymentLink); //NOTE: Yeah this will look nicer and we'll do QR codes as
            }
        }
    }

    /**
     * Deposit WRLD into this wallet
     *
     * @param amount
     * @param network
     * @param reason
     */
    public void payWRLD(double amount, Network network, String reason) {
        if (!NFTPlayer.getByUUID(getAssociatedPlayer()).isLinked()) return;
        if (!network.equals(Network.POLYGON)) {
            Bukkit.getLogger().warning("Attempted to call Wallet.payWRLD with unsupported network. " +
                    "Only Polygon is supported in this plugin at the moment.");
            return;
        }

        BigDecimal sending = Convert.toWei(BigDecimal.valueOf(amount), Convert.Unit.ETHER);
        Player paidPlayer = Objects.requireNonNull(Bukkit.getPlayer(this.getAssociatedPlayer()));
        paidPlayer.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.DARK_GREEN + "Incoming " + amount + " WRLD payment pending"));
        try {
            final PolygonWRLDToken polygonWRLDTokenContract = NFTWorlds.getInstance().getWrld().getPolygonWRLDTokenContract();
            polygonWRLDTokenContract.transfer(this.getAddress(), sending.toBigInteger()).sendAsync().thenAccept((c) -> {
                paidPlayer.sendMessage(
                        ChatColor.translateAlternateColorCodes('&',
                                "&6You've been paid! &7Reason&f: " + reason + "\n" +
                                        "&a&nhttps://polygonscan.com/tx/" +
                                        c.getTransactionHash() + "&r\n "));
            });
        } catch (Exception e) {
            Bukkit.getLogger().warning("caught error in payWrld:");
            e.printStackTrace();
        }

    }

    /**
     * Create a peer to peer payment link for player
     *
     * @param to
     * @param amount
     * @param network
     * @param reason
     */
    public void createPlayerPayment(NFTPlayer to, double amount, Network network, String reason) {
        NFTWorlds nftWorlds = NFTWorlds.getInstance();
        NFTPlayer nftPlayer = NFTPlayer.getByUUID(associatedPlayer);
        if (nftPlayer != null && to != null) {
            Player player = Bukkit.getPlayer(nftPlayer.getUuid());
            if (player != null) {
                if (!to.isLinked()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cThis player does not have a wallet linked."));
                    return;
                }
                Uint256 refID = new Uint256(new BigInteger(256, new Random()));
                long timeout = Instant.now().plus(nftWorlds.getNftConfig().getLinkTimeout(), ChronoUnit.SECONDS).toEpochMilli();
                new PeerToPeerPayment(to, nftPlayer, amount, refID, network, reason, timeout);
                String paymentLink = "https://nftworlds.com/pay/?to=" + to.getPrimaryWallet().getAddress() + "&amount=" + amount + "&ref=" + refID.getValue().toString() + "&expires=" + (int) (timeout / 1000);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f&lPAY HERE: ") + ChatColor.GREEN + paymentLink);
            }
        }
    }

}
