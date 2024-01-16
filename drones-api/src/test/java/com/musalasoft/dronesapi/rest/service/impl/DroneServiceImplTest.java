package com.musalasoft.dronesapi.rest.service.impl;

import com.musalasoft.dronesapi.core.exception.DroneAlreadyExistsException;
import com.musalasoft.dronesapi.core.exception.DroneNotFoundException;
import com.musalasoft.dronesapi.core.exception.DroneOverLoadException;
import com.musalasoft.dronesapi.core.utils.Constants;
import com.musalasoft.dronesapi.core.utils.payload.DroneModelMapper;
import com.musalasoft.dronesapi.model.entity.Drone;
import com.musalasoft.dronesapi.model.entity.Medication;
import com.musalasoft.dronesapi.model.enums.DroneModel;
import com.musalasoft.dronesapi.model.enums.DroneState;
import com.musalasoft.dronesapi.model.payload.drone.DroneDto;
import com.musalasoft.dronesapi.model.payload.drone.LoadDroneRequest;
import com.musalasoft.dronesapi.model.payload.drone.RegisterDroneRequest;
import com.musalasoft.dronesapi.model.payload.base.BaseResponse;
import com.musalasoft.dronesapi.model.payload.medication.FetchLoadedMedicationsResponse;
import com.musalasoft.dronesapi.model.repository.DroneRepository;
import com.musalasoft.dronesapi.model.repository.MediaRepository;
import com.musalasoft.dronesapi.model.repository.MedicationRepository;
import com.musalasoft.dronesapi.rest.service.BatteryLevelAuditService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DroneServiceImplTest {
    @Mock
    private DroneRepository droneRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private BatteryLevelAuditService batteryLevelAuditService;

    @Mock
    private MedicationRepository medicationRepository;

    private static Validator validator;

    @InjectMocks
    private DroneServiceImpl droneService;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void shouldTestRegisterDroneSuccessfullyRegistersWithCorrectParameters() {
        RegisterDroneRequest request = RegisterDroneRequest.builder()
                .serialNumber("ABC123")
                .model(DroneModel.LIGHTWEIGHT.name())
                .weightLimit(300.0)
                .batteryCapacity(80)
                .build();

        when(droneRepository.existsBySerialNumber(request.getSerialNumber())).thenReturn(false);

        Drone savedDrone = new Drone();
        savedDrone.setId(1L);
        savedDrone.setSerialNumber(request.getSerialNumber());
        savedDrone.setBatteryLevel(request.getBatteryCapacity());
        savedDrone.setWeightLimit(request.getWeightLimit());
        savedDrone.setDroneState(DroneState.IDLE);
        savedDrone.setModel(DroneModelMapper.mapModel(request.getModel()));

        when(droneRepository.save(any(Drone.class))).thenReturn(savedDrone);

        var result = droneService.registerDrone(request);

        verify(droneRepository, times(1)).existsBySerialNumber(request.getSerialNumber());
        verify(droneRepository, times(1)).save(any(Drone.class));

        DroneDto droneDto = extractBaseResponseData(result, DroneDto.class);
        assertNotNull(droneDto);
        assertEquals(savedDrone.getId(), droneDto.getId());
        assertEquals(savedDrone.getSerialNumber(), droneDto.getSerialNumber());
        assertEquals(savedDrone.getBatteryLevel(), droneDto.getBatteryLevel());
        assertEquals(savedDrone.getWeightLimit(), droneDto.getWeightLimit());
        assertEquals(savedDrone.getDroneState().name(), droneDto.getState());

        assertEquals(HttpStatus.OK.value(), result.getResponseCode());
        assertEquals("drone registered successfully", result.getResponseMessage());
    }

    @Test
    void shouldTestRegisterDroneThrowsDroneAlreadyExistsException() {
        RegisterDroneRequest request = RegisterDroneRequest.builder()
                .serialNumber("ABC123")
                .model(DroneModel.LIGHTWEIGHT.name())
                .weightLimit(300.0)
                .batteryCapacity(80)
                .build();

        when(droneRepository.existsBySerialNumber(request.getSerialNumber())).thenReturn(true);

        verify(droneRepository, never()).save(any(Drone.class));

        assertThrows(DroneAlreadyExistsException.class, () -> droneService.registerDrone(request));
    }

    @Test
    void shouldTestRegisterDroneValidationOfMoreThanHundredCharactersSerialNumberFails() {
        String serialNumberMoreThan100 = "gdhdhdhdhdhshhhshshsgdhdhdhdhdhshhhshshsgdhdhdhdhdhshhhshshsgdhdhdhdhdhshhhshshsgdhdhdhdhdhshhhshshsretaaa";
        RegisterDroneRequest request = RegisterDroneRequest.builder()
                .serialNumber(serialNumberMoreThan100)
                .model(DroneModel.LIGHTWEIGHT.name())
                .weightLimit(300.0)
                .batteryCapacity(80)
                .build();

        Set<ConstraintViolation<RegisterDroneRequest>> violations = validator.validate(request);

        assertTrue(violations.size() > 0, "Validation should produce violations");
        assertEquals("Serial number must be at most 100 characters", violations.iterator().next().getMessage());
    }

    @Test
    void shouldTestRegisterDroneValidationOfLessThanHundredCharactersSerialNumberIsSuccessful() {
        String serialNumberLessThan100 = "gdhdhdhdhdhshhhshshsgdhdhdhdhdhshhhshshsgdhdhdhdhdhshhhsh";
        RegisterDroneRequest request = RegisterDroneRequest.builder()
                .serialNumber(serialNumberLessThan100)
                .model(DroneModel.LIGHTWEIGHT.name())
                .weightLimit(300.0)
                .batteryCapacity(80)
                .build();

        Set<ConstraintViolation<RegisterDroneRequest>> violations = validator.validate(request);

        assertFalse(violations.size() > 0, "Validation should produce violations");
    }

    @Test
    void shouldTestRegisterDroneValidationOfWeightGreaterThan500Fails() {
        String serialNumberLessThan100 = "loremipsum";
        RegisterDroneRequest request = RegisterDroneRequest.builder()
                .serialNumber(serialNumberLessThan100)
                .model(DroneModel.LIGHTWEIGHT.name())
                .weightLimit(501.0)
                .batteryCapacity(80)
                .build();

        Set<ConstraintViolation<RegisterDroneRequest>> violations = validator.validate(request);

        assertTrue(violations.size() > 0, "Validation should produce violations");
    }

    @Test
    void shouldTestRegisterDroneValidationOfWeightGreaterThan500IsSuccessful() {
        String serialNumberLessThan100 = "loremipsum";
        RegisterDroneRequest request = RegisterDroneRequest.builder()
                .serialNumber(serialNumberLessThan100)
                .model(DroneModel.LIGHTWEIGHT.name())
                .weightLimit(500.0)
                .batteryCapacity(80)
                .build();

        Set<ConstraintViolation<RegisterDroneRequest>> violations = validator.validate(request);

        assertFalse(violations.size() > 0, "Validation should produce violations");
    }

    @Test
    void shouldTestRegisterDroneValidationOfBatteryPercentageLessThan100IsSuccessful() {
        String serialNumberLessThan100 = "loremipsum";
        RegisterDroneRequest request = RegisterDroneRequest.builder()
                .serialNumber(serialNumberLessThan100)
                .model(DroneModel.LIGHTWEIGHT.name())
                .weightLimit(500.0)
                .batteryCapacity(100)
                .build();

        Set<ConstraintViolation<RegisterDroneRequest>> violations = validator.validate(request);

        assertFalse(violations.size() > 0, "Validation should produce violations");
    }

    @Test
    void  shouldTestRegisterDroneValidationOfBatteryPercentageGreaterThan100Fails() {
        String serialNumberLessThan100 = "loremipsum";
        RegisterDroneRequest request = RegisterDroneRequest.builder()
                .serialNumber(serialNumberLessThan100)
                .model(DroneModel.LIGHTWEIGHT.name())
                .weightLimit(500.0)
                .batteryCapacity(101)
                .build();

        Set<ConstraintViolation<RegisterDroneRequest>> violations = validator.validate(request);

        assertTrue(violations.size() > 0, "Validation should produce violations");
    }

    @Test
    void shouldTestLoadDroneValidationOfInvalidMedicationNameCharacterFails() {
        String invalidName = "strepsils!!*#$";
        LoadDroneRequest request = LoadDroneRequest.builder()
                .droneSerialNumber("lorem")
                .medicationImageId(1L)
                .medicationWeight(30.0)
                .medicationCode("MED_2022")
                .medicationName(invalidName)
                .build();

        Set<ConstraintViolation<LoadDroneRequest>> violations = validator.validate(request);

        assertTrue(violations.size() > 0, "Validation should produce violations");
        assertEquals("medication name must only contain letters, numbers, '-', or '_'", violations.iterator().next().getMessage());
    }

    @Test
    void shouldTestLoadDroneValidationOfValidMedicationNamePasses() {
        String validName = "Aspirin";
        LoadDroneRequest request = LoadDroneRequest.builder()
                .droneSerialNumber("lorem")
                .medicationImageId(1L)
                .medicationWeight(30.0)
                .medicationCode("MED_2022")
                .medicationName(validName)
                .build();

        Set<ConstraintViolation<LoadDroneRequest>> violations = validator.validate(request);

        assertEquals(0, violations.size(), "Validation should not produce violations");
    }

    @Test
    void shouldTestLoadDroneValidationOfInvalidMedicationCodeCharacterFails() {
        String invalidCode = "strepsils!!*#$";
        LoadDroneRequest request = LoadDroneRequest.builder()
                .droneSerialNumber("lorem")
                .medicationImageId(1L)
                .medicationWeight(30.0)
                .medicationCode(invalidCode)
                .medicationName("lorem")
                .build();

        Set<ConstraintViolation<LoadDroneRequest>> violations = validator.validate(request);

        assertTrue(violations.size() > 0, "Validation should produce violations");
        assertEquals("medication code must only contain upper case letters, numbers, or '_'", violations.iterator().next().getMessage());
    }

    @Test
    void shouldTestLoadDroneSuccessfullyRegistersWithMedicationsUnderDroneWeightLimit() {
        LoadDroneRequest request = LoadDroneRequest.builder().droneSerialNumber("loremipsum")
                .medicationCode("abc123").medicationName("strepsils").medicationWeight(200.0)
                .medicationImageId(1L).build();

        Long droneId = 1L;

        Drone savedDrone = new Drone();
        savedDrone.setId(1L);
        savedDrone.setSerialNumber("loremipsum");
        savedDrone.setBatteryLevel(100.0);
        savedDrone.setWeightLimit(400.0);
        savedDrone.setDroneState(DroneState.LOADED);
        savedDrone.setModel(DroneModel.HEAVYWEIGHT);

        Medication savedMedication = new Medication();
        savedMedication.setCode(request.getMedicationCode());
        savedMedication.setName(request.getMedicationName());
        savedMedication.setId(1L);
        savedMedication.setWeight(100.0);
        savedMedication.setDrone(savedDrone);
        savedMedication.setImageUrl(Constants.Media.PLACEHOLDER_URL);

        savedDrone.setMedications(Set.of(savedMedication));

        when(droneRepository.existsBySerialNumberAndId(request.getDroneSerialNumber(), droneId)).thenReturn(true);
        when(droneRepository.findById(droneId)).thenReturn(Optional.of(savedDrone));
        when(mediaRepository.findById(request.getMedicationImageId())).thenReturn(Optional.empty());

        when(medicationRepository.save(any(Medication.class))).thenReturn(savedMedication);
        when(droneRepository.save(any(Drone.class))).thenReturn(savedDrone);

        var result = droneService.loadDrone(droneId, request);

        verify(droneRepository, times(1)).existsBySerialNumberAndId(request.getDroneSerialNumber(), droneId);
        verify(droneRepository, times(1)).save(any(Drone.class));

        DroneDto droneDto = extractBaseResponseData(result, DroneDto.class);
        assertNotNull(droneDto);
        assertEquals(savedDrone.getId(), droneDto.getId());
        assertEquals(savedDrone.getSerialNumber(), droneDto.getSerialNumber());
        assertEquals(savedDrone.getBatteryLevel(), droneDto.getBatteryLevel());
        assertEquals(savedDrone.getWeightLimit(), droneDto.getWeightLimit());
        assertEquals(savedDrone.getDroneState().name(), droneDto.getState());
        assertEquals(savedDrone.getMedications().stream().findFirst().get().getName(), savedMedication.getName());

        assertEquals(HttpStatus.OK.value(), result.getResponseCode());
        assertEquals("drone loaded successfully", result.getResponseMessage());
    }

    @Test
    void shouldTestLoadDroneFailsForMedicationOverDroneWeightLimit() {
        LoadDroneRequest request = LoadDroneRequest.builder().droneSerialNumber("loremipsum")
                .medicationCode("abc123").medicationName("strepsils").medicationWeight(500.0)
                .medicationImageId(1L).build();

        Long droneId = 1L;

        Drone savedDrone = new Drone();
        savedDrone.setId(1L);
        savedDrone.setSerialNumber("loremipsum");
        savedDrone.setBatteryLevel(100.0);
        savedDrone.setWeightLimit(400.0);
        savedDrone.setDroneState(DroneState.LOADED);
        savedDrone.setModel(DroneModel.HEAVYWEIGHT);

        Medication savedMedication = new Medication();
        savedMedication.setCode(request.getMedicationCode());
        savedMedication.setName(request.getMedicationName());
        savedMedication.setId(1L);
        savedMedication.setWeight(100.0);
        savedMedication.setDrone(savedDrone);
        savedMedication.setImageUrl(Constants.Media.PLACEHOLDER_URL);

        savedDrone.setMedications(Set.of(savedMedication));

        when(droneRepository.existsBySerialNumberAndId(request.getDroneSerialNumber(), droneId)).thenReturn(true);
        when(droneRepository.findById(droneId)).thenReturn(Optional.of(savedDrone));

        assertThrows(DroneOverLoadException.class, () -> droneService.loadDrone(droneId, request));
    }

    @Test
    void shouldTestLoadDroneFailsForMedicationUnderDroneWeightLimit() {
        LoadDroneRequest request = LoadDroneRequest.builder().droneSerialNumber("loremipsum")
                .medicationCode("abc123").medicationName("strepsils").medicationWeight(100.0)
                .medicationImageId(1L).build();

        Long droneId = 1L;

        Drone savedDrone = new Drone();
        savedDrone.setId(1L);
        savedDrone.setSerialNumber("loremipsum");
        savedDrone.setBatteryLevel(100.0);
        savedDrone.setWeightLimit(400.0);
        savedDrone.setDroneState(DroneState.IDLE);
        savedDrone.setModel(DroneModel.HEAVYWEIGHT);

        Medication savedMedication = new Medication();
        savedMedication.setCode(request.getMedicationCode());
        savedMedication.setName(request.getMedicationName());
        savedMedication.setId(1L);
        savedMedication.setWeight(100.0);
        savedMedication.setDrone(savedDrone);
        savedMedication.setImageUrl(Constants.Media.PLACEHOLDER_URL);

        savedDrone.setMedications(Set.of(savedMedication));

        when(droneRepository.existsBySerialNumberAndId(request.getDroneSerialNumber(), droneId)).thenReturn(true);
        when(droneRepository.findById(droneId)).thenReturn(Optional.of(savedDrone));
        when(droneRepository.save(any(Drone.class))).thenReturn(savedDrone);
        when(mediaRepository.findById(request.getMedicationImageId())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> droneService.loadDrone(droneId, request));
    }

    @Test
    void shouldTestGetLoadedMedicationsSuccessfulFetch() {
        Long droneId = 1L;
        Drone drone = new Drone();
        drone.setId(droneId);
        drone.setSerialNumber("ABC123");
        drone.setMedications(new HashSet<>());
        when(droneRepository.findMedicationsByDroneId(droneId)).thenReturn(Optional.of(drone));

        var result = droneService.getLoadedMedication(droneId);


        assertEquals(Constants.ResponseStatusCode.SUCCESS, result.getResponseCode());
        assertEquals("fetched medications records successfully for drone with id " + droneId, result.getResponseMessage());

        FetchLoadedMedicationsResponse responseData = extractBaseResponseData(result, FetchLoadedMedicationsResponse.class);
        assertNotNull(responseData);
        assertEquals(drone.getId(), responseData.getDroneId());
        assertEquals(drone.getSerialNumber(), responseData.getDroneSerialNumber());
        assertEquals(drone.getMedications().size(), responseData.getMedications().size());
    }

    @Test
    void shouldTestGetLoadedMedicationsWhenDroneNotFound() {
        Long droneId = 1L;
        when(droneRepository.findMedicationsByDroneId(droneId)).thenReturn(Optional.empty());
        assertThrows(DroneNotFoundException.class, () -> droneService.getLoadedMedication(droneId));
    }

    <T> T extractBaseResponseData(BaseResponse<?> response, Class<T> responseType) {
        return responseType.cast(response.getData());
    }
}