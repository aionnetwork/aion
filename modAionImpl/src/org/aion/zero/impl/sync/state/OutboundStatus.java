package org.aion.zero.impl.sync.state;

/**
 * <p>Currently just a two-state transition, given that a peer
 * has a higher difficulty than us, we are going to want to:</p>
 *
 * <li>
 *     <ul>Grab headers for the next N blocks from them</ul>
 *     <ul>Grab bodies for the next N headers received from peer</ul>
 * </li>
 *
 * This enum set forms that relationship:
 *
 * FREE =>
 * REQ_HEADER_SENT =>(send) => (resp) =>
 * POST_HEADER_FREE => REQ_BODY_SENT => (send) => (resp)
 * => FREE
 */
public enum OutboundStatus {
    FREE,
    REQ_HEADER_SENT,
    POST_HEADER_FREE,
    REQ_BODY_SENT,
}
