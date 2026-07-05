package com.ticketmaster.service;

import com.ticketmaster.controller.dto.ApiListDto;
import com.ticketmaster.controller.dto.CreateEventDto;
import com.ticketmaster.controller.dto.EventDto;
import com.ticketmaster.controller.dto.SeatDto;
import com.ticketmaster.entity.EventEntity;
import com.ticketmaster.entity.SeatEntity;
import com.ticketmaster.entity.SeatStatus;
import com.ticketmaster.exception.ResourceNotFoundException;
import com.ticketmaster.repository.EventRepository;
import com.ticketmaster.repository.SeatRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    public EventService(EventRepository eventRepository, SeatRepository seatRepository) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public EventDto createEvent(CreateEventDto dto) {
        EventEntity event = new EventEntity(dto.name(), dto.description());
        eventRepository.save(event);

        for (int i = 0; i < dto.settings().numberOfSeats(); i++) {
            seatRepository.save(new SeatEntity(event, "S" + i, SeatStatus.AVAILABLE));
        }
        return EventDto.fromEntity(event);
    }

    public ApiListDto<EventDto> findAll(int page, int pageSize) {
        Page<EventEntity> result = eventRepository.findAll(PageRequest.of(page, pageSize));
        return ApiListDto.from(result, EventDto::fromEntity);
    }

    public Optional<EventDto> findById(Long id) {
        return eventRepository.findById(id).map(EventDto::fromEntity);
    }

    public ApiListDto<SeatDto> findAllSeats(Long eventId, int page, int pageSize) {
        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found", "No event with id: " + eventId));
        Page<SeatEntity> result = seatRepository.findByEvent(event, PageRequest.of(page, pageSize));
        return ApiListDto.from(result, SeatDto::fromEntity);
    }
}
