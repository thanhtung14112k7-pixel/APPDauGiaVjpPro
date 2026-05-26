package com.auction.service;

import com.auction.dto.SocketResponse;

/**
 * RealtimeUpdateListener la interface cho cac man hinh muon nhan realtime event tu Server.
 *
 * Vi du:
 * - LiveBiddingController nhan BID_UPDATE de cap nhat gia moi.
 * - LiveBiddingController nhan TIME_UPDATE de cap nhat thoi gian con lai.
 * - LiveBiddingController nhan LIVE_ENTERED / LIVE_EXITED de cap nhat so nguoi xem.
 * - LiveBiddingController co the nhan STATUS_UPDATED khi Server chot format trang thai.
 *
 * ClientSocketService khong can biet controller cu the nao dang lang nghe.
 * Service chi can goi onRealtimeUpdate(...) cho tat ca listener da dang ky.
 */
public interface RealtimeUpdateListener {

    /**
     * Ham duoc goi khi ClientSocketService nhan duoc SocketResponse co type = EVENT.
     *
     * Luu y:
     * - Ham nay duoc goi tu thread doc socket.
     * - Neu Controller cap nhat JavaFX UI trong ham nay, can dung Platform.runLater(...).
     *
     * @param event realtime event Server gui ve
     */
    void onRealtimeUpdate(SocketResponse event);
}
