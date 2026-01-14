package com.opentable.privatedining.mapper;

import com.opentable.privatedining.dto.SpaceDTO;
import com.opentable.privatedining.model.Space;
import org.springframework.stereotype.Component;

@Component
public class SpaceMapper {

    public SpaceDTO toDTO(Space space) {
        if (space == null) {
            return null;
        }
        return new SpaceDTO(
                space.getId(),
                space.getName(),
                space.getMinCapacity(),
                space.getMaxCapacity()
        );
    }

    public Space toModel(SpaceDTO spaceDTO) {
        if (spaceDTO == null) {
            return null;
        }
        // Space ID is always generated server-side, ignore any client-provided ID
        return new Space(
                spaceDTO.getName(),
                spaceDTO.getMinCapacity(),
                spaceDTO.getMaxCapacity()
        );
    }
}