package org.ppum.agsa.sanitize;

import org.ppum.agsa.model.*;
import java.util.*;

public interface Sanitizer {
    SanitizationResult sanitize(UtilityDatabase original,
                                PeriodicityConstraints constraints,
                                Set<ItemsetKey> sensitive,
                                Map<ItemsetKey, PatternInfo> originalHuiUniverse);
}
