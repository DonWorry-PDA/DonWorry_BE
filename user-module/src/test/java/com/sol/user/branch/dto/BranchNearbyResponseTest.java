package com.sol.user.branch.dto;

import com.sol.user.branch.repository.BranchNearbyProjection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 거리 환산 계약을 고정한다. distanceMeters는 미터 반올림 정수, distanceKm는 소수 첫째 자리.
 * FE가 이 두 값으로 "800m / 1.2km" 표기를 분기하므로 환산이 흔들리면 화면 거리가 어긋난다.
 */
class BranchNearbyResponseTest {

    @Test
    void 미터는_반올림_정수로_환산된다() {
        BranchNearbyResponse res = BranchNearbyResponse.from(projection(320.6));

        assertThat(res.distanceMeters()).isEqualTo(321);
    }

    @Test
    void km는_소수_첫째_자리로_환산된다() {
        BranchNearbyResponse res = BranchNearbyResponse.from(projection(1234));

        assertThat(res.distanceKm()).isEqualTo(1.2);
    }

    @Test
    void km는_미터_반올림이_아닌_원본_거리로_환산된다() {
        // 149.5m: 미터 반올림(150) 기준이면 0.2km로 부풀지만, 원본 기준이면 0.1km다.
        BranchNearbyResponse res = BranchNearbyResponse.from(projection(149.5));

        assertThat(res.distanceMeters()).isEqualTo(150);
        assertThat(res.distanceKm()).isEqualTo(0.1);
    }

    @Test
    void 백미터_미만은_0_0km다() {
        BranchNearbyResponse res = BranchNearbyResponse.from(projection(49));

        assertThat(res.distanceMeters()).isEqualTo(49);
        assertThat(res.distanceKm()).isEqualTo(0.0);
    }

    @Test
    void 필드가_그대로_매핑된다() {
        BranchNearbyResponse res = BranchNearbyResponse.from(
                projection(7L, "남대문", "서울 중구", "02-1", "서울", 320));

        assertThat(res.id()).isEqualTo(7L);
        assertThat(res.name()).isEqualTo("남대문");
        assertThat(res.address()).isEqualTo("서울 중구");
        assertThat(res.phone()).isEqualTo("02-1");
        assertThat(res.region()).isEqualTo("서울");
        assertThat(res.distanceMeters()).isEqualTo(320);
        assertThat(res.distanceKm()).isEqualTo(0.3);
    }

    private BranchNearbyProjection projection(double distanceM) {
        return projection(1L, "지점", "주소", "전화", null, distanceM);
    }

    private BranchNearbyProjection projection(Long id, String name, String address,
                                              String phone, String region, double distanceM) {
        return new BranchNearbyProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getAddress() {
                return address;
            }

            @Override
            public String getPhone() {
                return phone;
            }

            @Override
            public String getRegion() {
                return region;
            }

            @Override
            public double getDistanceM() {
                return distanceM;
            }
        };
    }
}
