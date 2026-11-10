import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vnotes/main.dart' as app;

void main() {
  testWidgets('typed note survives switching between Material tabs', (
    tester,
  ) async {
    final rows = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(app.host, (
      call,
    ) async {
      switch (call.method) {
        case 'notes':
          return rows;
        case 'pending':
          return false;
        case 'present':
          return true;
        case 'settings':
          return <String, double>{'threshold': .01, 'silence': 2};
        case 'add':
          rows.add(
            jsonEncode({
              'id': '1',
              'text': call.arguments['text'],
              'createdAt': 1,
              'deletedAt': null,
            }),
          );
      }
      return null;
    });
    app.main();
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Walk the dog');
    await tester.pump();
    await tester.tap(find.byTooltip('Add'));
    await tester.pumpAndSettle();
    expect(find.text('Walk the dog'), findsOneWidget);
    await tester.tap(find.text('DONE'));
    await tester.pumpAndSettle();
    expect(find.text('Walk the dog'), findsNothing);
    await tester.tap(find.text('TODO'));
    await tester.pumpAndSettle();
    expect(find.text('Walk the dog'), findsOneWidget);
  });
}
