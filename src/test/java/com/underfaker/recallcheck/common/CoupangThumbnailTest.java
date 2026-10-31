package com.underfaker.recallcheck.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoupangThumbnailTest {

    @Test
    void 작은_쿠팡_썸네일은_큰_크기로() {
        assertEquals("https://thumbnail10.coupangcdn.com/thumbnails/remote/492x492ex/image/vendor_inventory/a1/b2.jpg",
                CoupangThumbnail.larger("https://thumbnail10.coupangcdn.com/thumbnails/remote/120x120ex/image/vendor_inventory/a1/b2.jpg"));
        assertEquals("https://thumbnail6.coupangcdn.com/thumbnails/remote/492x492ex/image/retail/x.webp",
                CoupangThumbnail.larger("https://thumbnail6.coupangcdn.com/thumbnails/remote/48x48/image/retail/x.webp"));
    }

    @Test
    void 이미_크거나_다른_주소는_그대로() {
        String big = "https://thumbnail6.coupangcdn.com/thumbnails/remote/1000x1000ex/image/retail/x.jpg";
        assertEquals(big, CoupangThumbnail.larger(big));
        String other = "https://image.coupangcdn.com/my/common/badge.png";
        assertEquals(other, CoupangThumbnail.larger(other));
        String elsewhere = "https://shop.example.com/thumbnails/remote/120x120ex/a.jpg";
        assertEquals(elsewhere, CoupangThumbnail.larger(elsewhere));
        assertNull(CoupangThumbnail.larger(null));
    }
}
