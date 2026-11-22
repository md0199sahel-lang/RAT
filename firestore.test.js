const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write tasks or focusSessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_1")
      .set({
        userId: ALICE_UID,
        title: "Unauthorized task",
        notes: "",
        category: "WORK",
        priority: "NORMAL",
        completed: false,
        focusMinutes: 0,
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("focusSessions").get()
  );
});

test("Owner can create, list, partially update, increment focusMinutes, and delete tasks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tasksRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");
  const created = new Date(Date.now() - 10000);

  // Create mirroring Kotlin CadenceRepository.createTask() (NO `id` field)
  await assertSucceeds(
    tasksRef.doc("task_1").set({
      userId: ALICE_UID,
      title: "Draft quarterly roadmap",
      notes: "Outline Q1 milestones",
      category: "WORK",
      priority: "HIGH",
      completed: false,
      focusMinutes: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List query mirroring Kotlin observeTasks()
  await assertSucceeds(tasksRef.orderBy("createdAt", "desc").get());

  // Partial edit / toggle completion
  await assertSucceeds(
    tasksRef.doc("task_1").update({
      title: "Draft Q1 roadmap",
      notes: "Updated outline",
      category: "WORK",
      priority: "HIGH",
      completed: true,
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Focus minutes increment update
  await assertSucceeds(
    tasksRef.doc("task_1").update({
      focusMinutes: 25,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Delete own task
  await assertSucceeds(tasksRef.doc("task_1").delete());
});

test("Task validation blocks @DocumentId `id` field, immutable field edits, ghost fields, and invalid bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tasksRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");
  const now = new Date(Date.now() - 5000);

  // Reject `id` key in create payload
  await assertFails(
    tasksRef.doc("task_with_id").set({
      id: "task_with_id",
      userId: ALICE_UID,
      title: "Bad payload",
      notes: "",
      category: "WORK",
      priority: "NORMAL",
      completed: false,
      focusMinutes: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject ghost field
  await assertFails(
    tasksRef.doc("task_ghost").set({
      userId: ALICE_UID,
      title: "Ghost field",
      notes: "",
      category: "WORK",
      priority: "NORMAL",
      completed: false,
      focusMinutes: 0,
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject invalid enum & future timestamp
  await assertFails(
    tasksRef.doc("task_bad_enum").set({
      userId: ALICE_UID,
      title: "Invalid category",
      notes: "",
      category: "GAMING",
      priority: "NORMAL",
      completed: false,
      focusMinutes: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    tasksRef.doc("task_future").set({
      userId: ALICE_UID,
      title: "Future timestamp",
      notes: "",
      category: "WORK",
      priority: "NORMAL",
      completed: false,
      focusMinutes: 0,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: now,
    })
  );

  // Create valid doc, then test immutable createdAt / userId update rejection
  await assertSucceeds(
    tasksRef.doc("task_valid").set({
      userId: ALICE_UID,
      title: "Valid task",
      notes: "",
      category: "PERSONAL",
      priority: "LOW",
      completed: false,
      focusMinutes: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    tasksRef.doc("task_valid").update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    tasksRef.doc("task_valid").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Cross-user isolation blocks Bob from Alice's tasks and focusSessions", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const now = new Date(Date.now() - 10000);
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("alice_task")
      .set({
        userId: ALICE_UID,
        title: "Alice Private Task",
        notes: "",
        category: "WORK",
        priority: "HIGH",
        completed: false,
        focusMinutes: 0,
        createdAt: now,
        updatedAt: now,
      });
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("focusSessions")
      .doc("alice_session")
      .set({
        userId: ALICE_UID,
        taskId: "alice_task",
        taskTitle: "Alice Private Task",
        durationMinutes: 25,
        category: "WORK",
        completedAt: now,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("alice_task")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("alice_task")
      .update({
        completed: true,
        updatedAt: new Date(Date.now() - 1000),
      })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("alice_task")
      .delete()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("focusSessions").get()
  );
});

test("Owner can create, list, and delete valid focusSessions; rejects invalid duration", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessionsRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("focusSessions");
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    sessionsRef.doc("sess_1").set({
      userId: ALICE_UID,
      taskId: "",
      taskTitle: "General Focus",
      durationMinutes: 25,
      category: "WELLNESS",
      completedAt: now,
    })
  );

  await assertSucceeds(sessionsRef.orderBy("completedAt", "desc").get());

  await assertFails(
    sessionsRef.doc("sess_bad").set({
      userId: ALICE_UID,
      taskId: "",
      taskTitle: "Too long",
      durationMinutes: 300,
      category: "WORK",
      completedAt: now,
    })
  );

  await assertSucceeds(sessionsRef.doc("sess_1").delete());
});
