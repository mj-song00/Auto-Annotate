package auto.annotate.domain.highlight.dto.response;

import java.util.Set;

public class HighlightResult {
    private final Set<String> hospitalsWith7DaysVisit;
    private final Set<String> hospitalizationHospitals;
    private final Set<String> surgeryHospitals;
    private final Set<String> drugsOver30Days;

    public HighlightResult(
            Set<String> hospitalsWith7DaysVisit,
            Set<String> hospitalizationHospitals,
            Set<String> surgeryHospitals,
            Set<String> drugsOver30Days
    ) {
        this.hospitalsWith7DaysVisit = Set.copyOf(hospitalsWith7DaysVisit);
        this.hospitalizationHospitals = Set.copyOf(hospitalizationHospitals);
        this.surgeryHospitals = Set.copyOf(surgeryHospitals);
        this.drugsOver30Days = Set.copyOf(drugsOver30Days);
    }

    public boolean has7DaysVisit(String hospitalName) {
        return hospitalsWith7DaysVisit.contains(hospitalName);
    }

    public boolean isHospitalizationHospital(String hospitalName) {
        return hospitalizationHospitals.contains(hospitalName);
    }

    public boolean isSurgeryHospital(String hospitalName) {
        return surgeryHospitals.contains(hospitalName);
    }

    public boolean hasDrugOver30Days(String drugName) {
        return drugsOver30Days.contains(drugName);
    }
}
