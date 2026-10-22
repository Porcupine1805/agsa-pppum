package org.ppum.agsa.sanitize;

import org.ppum.agsa.model.*;
import java.util.*;

public record SanitizationResult(String algorithm, UtilityDatabase sanitizedDatabase,
                                 List<Edit> edits, boolean feasible, String message,
                                 long runtimeMillis, SanitizationDiagnostics diagnostics,
                                 Map<ItemsetKey,PatternInfo> finalWitnessStates) {}
