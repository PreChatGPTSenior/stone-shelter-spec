package lab.stoneshelter.services;

import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.shared.StoneRagGroundingRequest;

public interface StoneRagGroundingProvider { StoneModelResponseEntity invoke(StoneRagGroundingRequest request, String repair); }
