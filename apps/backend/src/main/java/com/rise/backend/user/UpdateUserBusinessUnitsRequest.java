package com.rise.backend.user;

import java.util.List;

public record UpdateUserBusinessUnitsRequest(
        List<Integer> businessUnitIds
) {
}
