package com.petvacay.search.adapters.out.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface SitterViewMongoRepository extends MongoRepository<SitterViewDocument, UUID> {
}
