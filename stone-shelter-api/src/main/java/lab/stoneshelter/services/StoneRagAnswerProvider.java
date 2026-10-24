package lab.stoneshelter.services;

import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.shared.StoneRagGenerationRequest;

public interface StoneRagAnswerProvider { StoneModelResponseEntity invoke(StoneRagGenerationRequest request, String repair); }
