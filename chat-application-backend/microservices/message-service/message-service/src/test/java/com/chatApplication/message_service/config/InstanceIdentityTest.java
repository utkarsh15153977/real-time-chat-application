package com.chatApplication.message_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("InstanceIdentity")
class InstanceIdentityTest {

    @Test
    @DisplayName("should generate a non-null instance ID")
    void constructor_generatesNonNullId() {
        InstanceIdentity identity = new InstanceIdentity();
        assertThat(identity.getInstanceId()).isNotNull();
    }

    @Test
    @DisplayName("should generate a valid UUID format")
    void constructor_generatesValidUuid() {
        InstanceIdentity identity = new InstanceIdentity();
        assertThat(identity.getInstanceId())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("should return the same ID on repeated calls")
    void getInstanceId_returnsConsistentValue() {
        InstanceIdentity identity = new InstanceIdentity();
        String first = identity.getInstanceId();
        String second = identity.getInstanceId();
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("should generate unique IDs across instances")
    void constructor_generatesUniqueIds() {
        InstanceIdentity a = new InstanceIdentity();
        InstanceIdentity b = new InstanceIdentity();
        assertThat(a.getInstanceId()).isNotEqualTo(b.getInstanceId());
    }
}
