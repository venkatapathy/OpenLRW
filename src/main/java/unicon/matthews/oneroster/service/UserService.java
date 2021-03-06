package unicon.matthews.oneroster.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import unicon.matthews.Vocabulary;
import unicon.matthews.oneroster.Enrollment;
import unicon.matthews.oneroster.User;
import unicon.matthews.oneroster.exception.EnrollmentNotFoundException;
import unicon.matthews.oneroster.exception.UserNotFoundException;
import unicon.matthews.oneroster.service.repository.MongoUser;
import unicon.matthews.oneroster.service.repository.MongoUserRepository;

@Service
public class UserService {
  private static Logger logger = LoggerFactory.getLogger(UserService.class);
  
  private MongoUserRepository mongoUserRepository;
  private EnrollmentService enrollmentService;
  
  @Autowired
  public UserService(MongoUserRepository mongoUserRepository,
      EnrollmentService enrollmentService) {
    this.mongoUserRepository = mongoUserRepository;
    this.enrollmentService = enrollmentService;
  }

  public User findBySourcedId(final String tenantId, final String orgId, final String userSourcedId) throws UserNotFoundException {
    
    MongoUser mongoUser = mongoUserRepository.findByTenantIdAndOrgIdAndUserSourcedId(tenantId, orgId, userSourcedId);
    if (mongoUser == null) {
      throw new UserNotFoundException();
    }
    
    return mongoUser.getUser();
  }
  
  public User save(final String tenantId, final String orgId, User user) {
    if (StringUtils.isBlank(tenantId) 
        || StringUtils.isBlank(orgId)
        || user == null) {
      throw new IllegalArgumentException();
    }
    
    MongoUser existingMongoUser 
      = mongoUserRepository.findByTenantIdAndOrgIdAndUserSourcedId(tenantId, orgId, user.getSourcedId());
    MongoUser mongoUserToSave = null;
    
    if (existingMongoUser == null) {
      mongoUserToSave = 
        new MongoUser.Builder()
          .withTenantId(tenantId)
          .withOrgId(orgId)
          .withUser(fromUser(user, tenantId))
          .build();
    }
    else {
      mongoUserToSave = 
        new MongoUser.Builder()
          .withId(existingMongoUser.getId())
          .withTenantId(tenantId)
          .withOrgId(orgId)
          .withUser(fromUser(user, tenantId))
          .build();
    }
    
    MongoUser saved = mongoUserRepository.save(mongoUserToSave);
    
//    updateEnrollments(tenantId, orgId, saved);
    
    return saved.getUser();
  }

  private User fromUser(User from, final String tenantId) {
    
    User user = null;
    
    if (from != null && StringUtils.isNotBlank(tenantId)) {
      Map<String, String> extensions = new HashMap<>();
      extensions.put(Vocabulary.TENANT, tenantId);
      Map<String, String> metadata = from.getMetadata();
      if (metadata != null && !metadata.isEmpty()) {
        extensions.putAll(metadata);
      }
      
      String sourcedId = from.getSourcedId();
      if (StringUtils.isBlank(sourcedId)) {
        sourcedId = UUID.randomUUID().toString();
      }
      
      user
        = new User.Builder()
          .withEmail(from.getEmail())
          .withFamilyName(from.getFamilyName())
          .withGivenName(from.getGivenName())
          .withIdentifier(from.getIdentifier())
          .withMetadata(metadata)
          .withPhone(from.getPhone())
          .withRole(from.getRole())
          .withSms(from.getSms())
          .withSourcedId(sourcedId)
          .withStatus(from.getStatus())
          .withUserId(from.getUserId())
          .withUsername(from.getUsername())
          .build();
    }
    
    return user;

  }

  @Async
  public void updateEnrollments(String tenantId, String orgId, MongoUser mongoUser) {
    try {
      Collection<Enrollment> userEnrollments = enrollmentService.findEnrollmentsForUser(tenantId, orgId, mongoUser.getUser().getSourcedId());
      
      if (userEnrollments != null) {
        for (Enrollment enrollment : userEnrollments) {
          Enrollment updatedEnrollment
            = new Enrollment.Builder()
              .withKlass(enrollment.getKlass())
              .withMetadata(enrollment.getMetadata())
              .withPrimary(enrollment.isPrimary())
              .withRole(enrollment.getRole())
              .withSourcedId(enrollment.getSourcedId())
              .withStatus(enrollment.getStatus())
              .withUser(mongoUser.getUser())
              .build();
          
          enrollmentService.save(tenantId, orgId, updatedEnrollment);
        }
      }
      
    } 
    catch (EnrollmentNotFoundException e) {
      logger.info("No enrollments found for user");
    }
  }

}
